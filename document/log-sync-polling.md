# new-api 日志同步方案：轮询 + 客户端过滤

## 场景

用户在控制台点击"发送"按钮后，系统开始监听该 API Key 的请求日志。每次拉取最近 10 条，过滤掉点击之前的历史数据，去重后写入自有数据库。

---

## 接口

```
GET /api/log/token
Authorization: Bearer sk-xxxxx
```

返回最近 ≤1000 条日志（数组，按 `created_at DESC`）。我们每次只取前 10 条。

### 返回字段

```json
[
  {
    "id": 12345,
    "request_id": "req-abc-001",
    "upstream_request_id": "up-xyz-001",
    "created_at": 1754000000,
    "type": 2,
    "content": "模型请求 gpt-4: ...",
    "model_name": "gpt-4",
    "token_name": "my-key",
    "channel": 3,
    "prompt_tokens": 150,
    "completion_tokens": 300,
    "quota": 900,
    "use_time": 2,
    "is_stream": true,
    "ip": "1.2.3.4",
    "other": "{\"model_ratio\":1.5}"
  }
]
```

### 字段映射

| JSON 字段 | 本地数据库列 | 注意 |
|---|---|---|
| `request_id` | `request_id` | 去重键 |
| `type` | `type` | 去重键（同一 request_id 可能有 type=5 + type=2 两条） |
| `upstream_request_id` | `upstream_request_id` | |
| `created_at` | `created_at` | Unix 秒，时间过滤依据 |
| `model_name` | `model_name` | |
| `token_name` | `token_name` | |
| `channel` | `channel_id` | ⚠️ JSON key 是 `channel`，不是 `channel_id` |
| `prompt_tokens` | `prompt_tokens` | |
| `completion_tokens` | `completion_tokens` | |
| `quota` | `quota` | |
| `use_time` | `use_time` | |
| `is_stream` | `is_stream` | true→1, false→0 |
| `content` | `content` | |
| `other` | `other` | JSON 字符串 |
| `ip` | `ip` | |
| `id` | `new_api_log_id` | 原始 log ID，仅参考 |

---

## 建表 DDL

在你自己的数据库执行：

```sql
CREATE TABLE api_request_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '本地自增主键',

    -- 去重键
    request_id          VARCHAR(64)  NOT NULL COMMENT 'new-api请求追踪ID',
    type                TINYINT      NOT NULL DEFAULT 2 COMMENT '日志类型：2=消费 5=错误',

    -- 业务字段
    upstream_request_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '上游请求ID',
    created_at          BIGINT       NOT NULL COMMENT '请求时间，Unix秒',
    model_name          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '模型名称',
    token_name          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '令牌名称',
    channel_id          INT          NOT NULL DEFAULT 0 COMMENT '渠道ID',
    prompt_tokens       INT          NOT NULL DEFAULT 0 COMMENT '提示词Token数',
    completion_tokens   INT          NOT NULL DEFAULT 0 COMMENT '补全Token数',
    quota               INT          NOT NULL DEFAULT 0 COMMENT '消耗配额',
    use_time            INT          NOT NULL DEFAULT 0 COMMENT '请求耗时（秒）',
    is_stream           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否流式：0=否 1=是',
    content             TEXT         COMMENT '日志内容',
    other               TEXT         COMMENT '扩展JSON（计费详情等）',
    ip                  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端IP',
    group_col           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分组标识',
    new_api_log_id      INT          NOT NULL DEFAULT 0 COMMENT '原始log.id，仅参考',

    -- 同步管理
    synced_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
    sync_batch          VARCHAR(36)  COMMENT '批次号',

    -- 唯一约束：同一请求的不同类型日志都保留
    UNIQUE KEY uk_request_type (request_id, type),

    -- 索引
    INDEX idx_created_at (created_at),
    INDEX idx_model_name (model_name)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='new-api请求日志同步表';
```

---

## 同步逻辑

```
用户点击"发送"
    │
    ├─ startTs = now()             ← 分界线
    │
    └─ 开始循环 ──────────────────────────────┐
          │                                    │
          GET /api/log/token                   │
          │  返回最多 1000 条（DESC）          │
          │  只取前 10 条                     │
          │                                    │
          对每条:                               │
          │  created_at < startTs → 跳过       │
          │  request_id+type 已存在 → 跳过     │
          │  其余 → INSERT IGNORE              │
          │                                    │
          sleep(10s) ──────────────────────────┘
```

### 过滤规则

| 规则 | 条件 | 处理 |
|---|---|---|
| 时间过滤 | `created_at < startTs` | 丢弃（用户点击前产生的日志） |
| 去重 | `(request_id, type)` 已存在于本地库 | 丢弃（`INSERT IGNORE` 自动跳过） |
| 保留 | 以上都不满足 | 写入 |

### 为什么取 10 条就够了

- 单人使用场景，10 秒内最多产生几十条请求
- 取前 10 条足够覆盖增量，超出部分下一轮也会拉到
- 如果 10 条中有 8 条已经被去重（历史数据），剩下 2 条是新的，刚好就是这轮的增量
- 极端情况 10 秒内超过 10 条新请求：下一轮继续拉，不会丢

---

## Java 实现

### LogPoller.java

```java
package com.example.sync;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * new-api 日志轮询同步器。
 * 每次拉取最近 10 条，过滤+去重后写入本地数据库。
 */
public class LogPoller {

    private final String apiBaseUrl;
    private final String apiKey;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;

    private volatile boolean running = false;

    public LogPoller(String apiBaseUrl, String apiKey,
                     String jdbcUrl, String dbUser, String dbPassword) {
        this.apiBaseUrl = apiBaseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    /**
     * 启动轮询。
     * @param startTs 用户点击"发送"时的 Unix 秒时间戳
     */
    public void start(long startTs) {
        if (running) return;
        running = true;

        System.out.printf("[LogPoller] 启动，startTs=%d (%s)%n",
                startTs, Instant.ofEpochSecond(startTs));

        while (running) {
            try {
                int n = syncOnce(startTs);
                if (n > 0) {
                    System.out.printf("[LogPoller] 写入 %d 条%n", n);
                }
                sleep(10_000);
            } catch (Exception e) {
                System.err.printf("[LogPoller] 异常: %s，30秒后重试%n", e.getMessage());
                sleep(30_000);
            }
        }
    }

    public void stop() {
        running = false;
        System.out.println("[LogPoller] 已停止");
    }

    // ========================================================
    // 单次同步：拉接口 → 取前10条 → 过滤 → 写入
    // ========================================================
    private int syncOnce(long startTs) throws Exception {
        // 1. 请求 new-api
        String json = httpGet(apiBaseUrl + "/api/log/token", apiKey);

        // 2. 解析，只取前 10 条
        List<JsonObj> batch = parseLogs(json, 10);
        if (batch.isEmpty()) return 0;

        // 3. 写入数据库
        String batchId = UUID.randomUUID().toString();
        int count = 0;

        String sql = """
            INSERT IGNORE INTO api_request_logs (
                request_id, type, upstream_request_id, created_at,
                model_name, token_name, channel_id,
                prompt_tokens, completion_tokens, quota, use_time, is_stream,
                content, other, ip, group_col, new_api_log_id,
                synced_at, sync_batch
            ) VALUES (?,?,?,?,  ?,?,?,  ?,?,?,?,?,  ?,?,?,?,?,  NOW(),?)
            """;

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (JsonObj log : batch) {
                // ---- 时间过滤 ----
                long createdAt = log.longVal("created_at");
                if (createdAt < startTs) continue;

                // ---- 绑定参数 ----
                ps.setString(1,  log.str("request_id"));
                ps.setInt(2,     log.intVal("type"));
                ps.setString(3,  log.str("upstream_request_id"));
                ps.setLong(4,    createdAt);
                ps.setString(5,  log.str("model_name"));
                ps.setString(6,  log.str("token_name"));
                ps.setInt(7,     log.intVal("channel"));       // JSON key 是 channel
                ps.setInt(8,     log.intVal("prompt_tokens"));
                ps.setInt(9,     log.intVal("completion_tokens"));
                ps.setInt(10,    log.intVal("quota"));
                ps.setInt(11,    log.intVal("use_time"));
                ps.setInt(12,    log.boolVal("is_stream") ? 1 : 0);
                ps.setString(13, log.str("content"));
                ps.setString(14, log.str("other"));
                ps.setString(15, log.str("ip"));
                ps.setString(16, "");
                ps.setInt(17,    log.intVal("id"));
                ps.setString(18, batchId);
                ps.addBatch();

                count++;
            }

            ps.executeBatch();
            conn.commit();
        }

        return count;
    }

    // ========================================================
    // HTTP / JSON / DB 工具
    // ========================================================
    private String httpGet(String url, String auth) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + auth)
                .timeout(java.time.Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    /** 解析接口返回的 JSON，取前 N 条 */
    private List<JsonObj> parseLogs(String raw, int limit) {
        List<JsonObj> list = new ArrayList<>();
        // 简单字符串解析，不引入第三方 JSON 库
        int pos = raw.indexOf("\"data\"");
        if (pos < 0) return list;
        pos = raw.indexOf('[', pos);
        if (pos < 0) return list;

        int depth = 0, start = pos;
        for (int i = pos; i < raw.length() && list.size() < limit; i++) {
            char c = raw.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    list.add(new JsonObj(raw.substring(start, i + 1)));
                }
            }
        }
        return list;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ========================================================
    // 轻量 JSON 取值（不依赖第三方库）
    // ========================================================
    static class JsonObj {
        private final String raw;
        JsonObj(String raw) { this.raw = raw; }

        String str(String key) {
            String pat = "\"" + key + "\"";
            int i = raw.indexOf(pat);
            if (i < 0) return "";
            i = raw.indexOf('"', i + pat.length());
            if (i < 0) return "";
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < raw.length()) {
                char c = raw.charAt(i);
                if (c == '\\') { sb.append(raw.charAt(i + 1)); i += 2; continue; }
                if (c == '"') break;
                sb.append(c);
                i++;
            }
            return sb.toString();
        }

        int intVal(String key) {
            String s = str(key);
            if (s.isEmpty()) return 0;
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }

        long longVal(String key) {
            String s = str(key);
            if (s.isEmpty()) return 0;
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        }

        boolean boolVal(String key) {
            return "true".equals(str(key));
        }
    }

    // ========================================================
    // 入口
    // ========================================================
    public static void main(String[] args) {
        LogPoller poller = new LogPoller(
            "https://your-new-api.com",
            "sk-xxxxxxxxxxxx",
            "jdbc:mysql://localhost:3306/your_db?serverTimezone=UTC",
            "root",
            "your_password"
        );

        long startTs = System.currentTimeMillis() / 1000;

        Runtime.getRuntime().addShutdownHook(new Thread(poller::stop));
        poller.start(startTs);
    }
}
```

### Spring Boot 集成

```java
@Service
public class LogSyncService {

    private LogPoller poller;

    @Value("${newapi.url}")
    private String apiBaseUrl;

    @Value("${newapi.apikey}")
    private String apiKey;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    /** 用户点击"发送"按钮时调用 */
    public void onUserClickSend() {
        long startTs = System.currentTimeMillis() / 1000;
        new Thread(new Runnable() {
            @Override
            public void run() {
                poller = new LogPoller(apiBaseUrl, apiKey, jdbcUrl, dbUser, dbPassword);
                poller.start(startTs);
            }
        }).start();
    }

    /** 用户关闭页面时调用 */
    public void onUserClose() {
        if (poller != null) poller.stop();
    }
}
```

---

## 时序示例

```
14:00:00  用户点击"发送" → startTs = 1754006400
14:00:01  第1次请求完成 → new-api 写入 log-1 (created_at=1754006401)
14:00:03  第2次请求完成 → new-api 写入 log-2 (created_at=1754006403)

14:00:10  轮询第1轮：
          GET /api/log/token 返回 [log-2, log-1, ...历史数据...]
          取前10条 → [log-2, log-1, 8条历史]
          log-2: created_at=1754006403 >= startTs ✓ → INSERT
          log-1: created_at=1754006401 >= startTs ✓ → INSERT
          历史8条: created_at < startTs ✗ → 跳过
          写入 2 条

14:00:15  第3次请求完成 → new-api 写入 log-3 (created_at=1754006415)

14:00:20  轮询第2轮：
          GET /api/log/token 返回 [log-3, log-2, log-1, ...]
          取前10条 → [log-3, log-2, log-1, ...]
          log-3: 新 → INSERT
          log-2: 已存在 → INSERT IGNORE 跳过
          log-1: 已存在 → INSERT IGNORE 跳过
          ...
          写入 1 条

14:00:30  轮询第3轮：
          GET /api/log/token，没有新日志
          前10条全是历史/已存在 → 写入 0 条
```

---

## 查询示例

```sql
-- 最近日志
SELECT request_id, model_name, quota,
       FROM_UNIXTIME(created_at) AS request_time
FROM api_request_logs
ORDER BY created_at DESC
LIMIT 20;

-- 按模型汇总
SELECT model_name,
       COUNT(*) AS cnt,
       SUM(quota) AS total_quota,
       SUM(prompt_tokens) AS total_prompt,
       SUM(completion_tokens) AS total_completion
FROM api_request_logs
WHERE type = 2
GROUP BY model_name;
```

> 📅 2026-07-31
