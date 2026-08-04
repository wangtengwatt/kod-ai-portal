package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.entity.*;
import com.kod.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 日志轮询同步服务。
 *
 * <p>用户点击"发送"后启动轮询线程，每 60s 调用 new-api 的
 * {@code GET /api/log/token} 拉取最多 10 条新日志，经过时间过滤 +
 * (request_id, type) 去重后写入 api_request_logs 表，然后实时扣减余额并
 * 聚合到看板表。</p>
 *
 * <p>停止条件（任一满足即停）：
 * <ul>
 *   <li>user.connect 变为 null（释放 key / 切换节点）</li>
 *   <li>连续 5 分钟无新日志（空闲超时）</li>
 *   <li>手动调用 {@link #onUserClose()}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogSyncService {

    private final ApiRequestLogMapper logMapper;
    private final DashboardHourlyMapper hourlyMapper;
    private final DashboardModelSummaryMapper summaryMapper;
    private final UserMapper userMapper;
    private final RelayStationMapper stationMapper;
    private final RelayStationKeyMapper keyMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 每 quota 折合 1 元（与 new-api 保持一致）。 */
    private static final int QUOTA_PER_UNIT = 500_000;

    /** 空闲超时：连续无新日志 5 分钟后自动停止。 */
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile boolean running = false;
    private long startTs;
    private Long userId;
    /** 上次发现新日志的时间戳（毫秒），用于空闲超时检测。 */
    private long lastNewLogTime;

    // -------------------------------------------------------
    // 生命周期
    // -------------------------------------------------------

    /** 用户点击"发送"时调用。已在运行则忽略（幂等）。 */
    public void onUserClickSend(Long userId) {
        // 同一用户已在运行 → 忽略
        if (running && userId.equals(this.userId)) {
            log.debug("[LogSync] 已在运行中，忽略，userId={}", userId);
            // 刷新 lastNewLogTime：用户刚发了消息，算作活动
            this.lastNewLogTime = System.currentTimeMillis();
            return;
        }

        // 不同用户的旧轮询 → 停掉
        if (running) {
            log.info("[LogSync] 停止旧轮询（切换用户），oldUserId={}, newUserId={}", this.userId, userId);
            running = false;
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        this.userId = userId;
        this.startTs = System.currentTimeMillis() / 1000;
        this.lastNewLogTime = System.currentTimeMillis();
        running = true;

        log.info("[LogSync] 启动，userId={}, startTs={}", userId, startTs);

        new Thread(() -> {
            while (running) {
                try {
                    // 检查 connect，为空则停止
                    if (!isUserStillConnected()) {
                        log.info("[LogSync] user.connect 已为空，自动停止，userId={}", userId);
                        running = false;
                        break;
                    }

                    // 空闲超时检查（连续 5 分钟无新日志）
                    if (System.currentTimeMillis() - lastNewLogTime > IDLE_TIMEOUT_MS) {
                        log.info("[LogSync] 空闲超时（{}ms），自动停止，userId={}", IDLE_TIMEOUT_MS, userId);
                        running = false;
                        break;
                    }

                    int n = syncOnce();
                    if (n > 0) {
                        lastNewLogTime = System.currentTimeMillis();  // 有新日志 → 刷新活动时间
                        log.info("[LogSync] 本轮写入 {} 条", n);
                    }
                    sleep(60_000);
                } catch (Exception e) {
                    log.warn("[LogSync] 异常: {}，30s 后重试", e.getMessage());
                    sleep(30_000);
                }
            }
            releaseKeyOnStop();
        }, "log-sync-" + userId).start();
    }

    /** 停止轮询（用户关闭页面 / 释放 key / 手动停止）。 */
    public void onUserClose() {
        running = false;
        log.info("[LogSync] 已停止，userId={}", userId);
        userId = null;
    }

    public boolean isRunning() { return running; }

    // -------------------------------------------------------
    // 检查连接状态
    // -------------------------------------------------------

    /** 轮询结束后释放 key + 清空 connect。 */
    private void releaseKeyOnStop() {
        try {
            User user = userMapper.selectById(userId);
            if (user != null && user.getConnect() != null) {
                RelayStationKey key = keyMapper.selectById(user.getConnect());
                if (key != null) {
                    key.setStatus(0);
                    keyMapper.updateById(key);
                }
                user.setConnect(null);
                userMapper.update(null,
                        Wrappers.<User>lambdaUpdate()
                                .set(User::getConnect, null)
                                .eq(User::getId, userId));
                log.info("[LogSync] 轮询结束，自动释放 key，userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("[LogSync] 释放 key 失败: {}", e.getMessage());
        }
    }

    private boolean isUserStillConnected() {
        if (userId == null) return false;
        User user = userMapper.selectById(userId);
        return user != null && user.getConnect() != null;
    }

    // -------------------------------------------------------
    // 单次同步（对齐 log-sync-polling.md 方案）
    // -------------------------------------------------------

    /**
     * 单轮同步：
     * <ol>
     *   <li>拿到当前 connect 对应的 API Key 和中转站 URL</li>
     *   <li>GET {url}/api/log/token → 取前 10 条</li>
     *   <li>created_at < startTs 的丢弃</li>
     *   <li>(request_id, type) 已存在的丢弃</li>
     *   <li>其余写入 api_request_logs</li>
     *   <li>扣减余额 + 聚合看板</li>
     * </ol>
     */
    private int syncOnce() throws Exception {
        User user = userMapper.selectById(userId);
        if (user == null || user.getConnect() == null) return 0;

        // 查出中转站 url 和 api_key
        RelayStationKey key = keyMapper.selectById(user.getConnect());
        if (key == null) return 0;
        RelayStation station = stationMapper.selectById(key.getStationId());
        if (station == null || station.getUrl() == null) return 0;

        String apiKey = key.getApiKey();
        String apiBase = station.getUrl().replaceAll("/v1/?$", "").replaceAll("/$", "");

        // 1. 请求 new-api
        String url = apiBase + "/api/log/token";
        log.info("[LogSync] 请求 new-api: {}", url);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("[LogSync] new-api 返回 HTTP {}, body={}", resp.statusCode(),
                    resp.body().substring(0, Math.min(200, resp.body().length())));
            return 0;
        }

        // 2. 解析，只取前 10 条
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            log.info("[LogSync] 响应无 data 数组，body 前100字: {}",
                    resp.body().substring(0, Math.min(100, resp.body().length())));
            return 0;
        }
        log.info("[LogSync] 收到 {} 条日志", data.size());

        String batchId = UUID.randomUUID().toString();
        int count = 0;
        int totalNewQuota = 0;

        for (int i = 0; i < Math.min(data.size(), 10); i++) {
            JsonNode item = data.get(i);

            // 时间过滤
            long createdAt = item.path("created_at").asLong();
            if (createdAt < startTs) continue;

            // 只处理消费日志
            int type = item.path("type").asInt(2);
            if (type != 2 && type != 5) continue;

            String requestId = item.path("request_id").asText("");

            // 去重
            Long exists = logMapper.selectCount(
                    Wrappers.<ApiRequestLog>lambdaQuery()
                            .eq(ApiRequestLog::getRequestId, requestId)
                            .eq(ApiRequestLog::getType, type));
            if (exists != null && exists > 0) continue;

            ApiRequestLog entry = new ApiRequestLog();
            entry.setUserId(userId);
            entry.setRequestId(requestId);
            entry.setType(type);
            entry.setUpstreamRequestId(item.path("upstream_request_id").asText(""));
            entry.setCreatedAt(createdAt);
            entry.setModelName(item.path("model_name").asText(""));
            entry.setTokenName(item.path("token_name").asText(""));
            entry.setChannelId(item.path("channel").asInt(0));
            entry.setPromptTokens(item.path("prompt_tokens").asInt(0));
            entry.setCompletionTokens(item.path("completion_tokens").asInt(0));
            entry.setQuota(item.path("quota").asInt(0));
            entry.setUseTime(item.path("use_time").asInt(0));
            entry.setIsStream(item.path("is_stream").asBoolean() ? 1 : 0);
            entry.setContent(item.path("content").asText(""));
            entry.setOther(item.path("other").asText(""));
            entry.setIp(item.path("ip").asText(""));
            entry.setGroupCol("");
            entry.setNewApiLogId(item.path("id").asInt(0));
            entry.setSyncedAt(LocalDateTime.now());
            entry.setSyncBatch(batchId);

            try {
                logMapper.insert(entry);
                count++;
                if (type == 2) totalNewQuota += entry.getQuota();
            } catch (Exception e) {
                // 唯一键冲突 → 跳过
            }
        }

        // 3. 扣款 + 聚合
        if (count > 0) {
            log.info("[LogSync] 本轮有新日志 count={}, totalNewQuota={}, 准备聚合", count, totalNewQuota);
            deductAndAggregate(user, totalNewQuota);
        } else {
            log.info("[LogSync] 本轮无新日志");
        }

        return count;
    }

    // -------------------------------------------------------
    // 扣款 + 看板聚合
    // -------------------------------------------------------

    private void deductAndAggregate(User user, int totalNewQuota) {
        if (totalNewQuota <= 0) {
            log.info("[LogSync] totalNewQuota={} <= 0，跳过扣款和聚合", totalNewQuota);
            return;
        }
        log.info("[LogSync] 开始扣款和聚合，quota={}", totalNewQuota);

        // 扣减余额：消费金额 = quota / QUOTA_PER_UNIT
        BigDecimal cost = BigDecimal.valueOf(totalNewQuota)
                .divide(BigDecimal.valueOf(QUOTA_PER_UNIT), 6, RoundingMode.HALF_UP);

        BigDecimal currentBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.subtract(cost);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("[LogSync] 余额不足！userId={}, balance={}, cost={}", user.getId(), currentBalance, cost);
            newBalance = BigDecimal.ZERO;
        }

        BigDecimal historical = user.getHistoricalConsumption() != null
                ? user.getHistoricalConsumption() : BigDecimal.ZERO;

        user.setBalance(newBalance);
        user.setHistoricalConsumption(historical.add(cost));
        userMapper.updateById(user);
        log.info("[LogSync] 扣款完成，userId={}, cost={}, newBalance={}", user.getId(), cost, newBalance);

        // 聚合到 dashboard_hourly（使用最新一批日志）
        aggregateDashboard();
    }

    private void aggregateDashboard() {
        try {
            int h = hourlyMapper.aggregateFromLogs();
            int s = summaryMapper.aggregateFromLogs();
            log.info("[LogSync] 看板聚合完成，hourly={}, summary={}", h, s);
        } catch (Exception e) {
            log.error("[LogSync] 看板聚合失败: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------
    // 工具
    // -------------------------------------------------------

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
