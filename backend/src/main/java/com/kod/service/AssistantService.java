package com.kod.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.config.AssistantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 蒜宝助手服务 —— 代理 LLM 流式对话，支持速率限制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantProperties props;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String REDIS_PREFIX = "assistant:";

    /** 根据 currentPath 构建系统提示词。 */
    private String buildSystemPrompt(String currentPath) {
        String pathContext = switch (currentPath != null ? currentPath : "") {
            case "/kai" -> "用户正在浏览 KAI 期算白皮书页面。";
            case "/features" -> "用户正在浏览产品特性页面。";
            case "/download" -> "用户正在浏览下载页面。";
            case "/pricing", "/console/wallet" -> "用户正在浏览定价/钱包页面。";
            case "/console" -> "用户正在控制台概览页面。";
            case "/console/logs" -> "用户正在查看操作日志。";
            default -> "用户在 KOD 官网浏览。";
        };

        return """
                你是"蒜宝"，KOD蒜粒官网的智能助手。你的特点：
                - 友好、热情、专业
                - 帮助用户了解 KOD蒜粒 产品、KAI 期算标准、API 接入方式
                - 可以推荐用户访问相关页面

                产品核心信息：
                - KOD蒜粒 是 AI 算力交易平台，基于 KAI 期算标准
                - KAI 期算：标准化 AI 模型容量期货合约，单位 TPM-h
                - 支持桌面端（Windows/Mac/Linux）和 Web 端
                - 登录后可进入控制台管理 API Key、充值、查看用量
                - 充值支持支付宝、微信支付

                可推荐页面：
                - /kai - KAI 期算白皮书
                - /features - 产品特性
                - /download - 下载页面
                - /console - 控制台（需登录）
                - /console/wallet - 钱包充值
                - /login - 登录页面
                - /register - 注册页面

                %s
                请用中文回答，简洁友好，不超过 300 字。如果用户询问具体操作，可以推荐相关页面链接。
                """.formatted(pathContext);
    }

    /**
     * 处理流式对话请求，返回 SseEmitter。
     */
    @SuppressWarnings("unchecked")
    public SseEmitter chat(String deviceId, String currentPath, List<Map<String, Object>> messages,
                           String ip) {
        SseEmitter emitter = new SseEmitter(60_000L);

        if (!props.isEnabled()) {
            sendEvent(emitter, "error", Map.of("code", "DISABLED", "message", "蒜宝暂时不可用"));
            emitter.complete();
            return emitter;
        }

        // 速率限制检查
        String rateLimitError = checkRateLimit(ip, deviceId);
        if (rateLimitError != null) {
            sendEvent(emitter, "error", Map.of("code", "RATE_LIMITED", "message", rateLimitError));
            emitter.complete();
            return emitter;
        }

        // 输入校验
        if (messages == null || messages.isEmpty()) {
            sendEvent(emitter, "error", Map.of("code", "EMPTY_INPUT", "message", "请输入内容"));
            emitter.complete();
            return emitter;
        }

        // 截断消息列表
        List<Map<String, Object>> truncated = messages.stream()
                .limit(props.getMaxMessages())
                .toList();

        // 构建消息体
        List<Map<String, Object>> apiMessages = new ArrayList<>();
        apiMessages.add(Map.of("role", "system", "content", buildSystemPrompt(currentPath)));
        apiMessages.addAll(truncated);

        // 异步调用上游 LLM
        new Thread(() -> {
            try {
                String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                sendEvent(emitter, "start", Map.of("messageId", messageId));

                // 调用上游 API
                Map<String, Object> reqBody = new LinkedHashMap<>();
                reqBody.put("model", props.getModel());
                reqBody.put("messages", apiMessages);
                reqBody.put("stream", true);
                reqBody.put("max_tokens", 512);
                reqBody.put("temperature", 0.7);

                String apiUrl = props.getBaseUrl() + "/chat/completions";
                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + props.getApiKey());
                conn.setDoOutput(true);
                conn.setConnectTimeout(parseTimeout(props.getConnectTimeout()));
                conn.setReadTimeout(parseTimeout(props.getStreamTimeout()));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(mapper.writeValueAsBytes(reqBody));
                }

                int status = conn.getResponseCode();
                if (status != 200) {
                    String errBody = new BufferedReader(new InputStreamReader(conn.getErrorStream()))
                            .lines().collect(Collectors.joining("\n"));
                    log.warn("Assistant upstream error {}: {}", status, errBody);
                    sendEvent(emitter, "error", Map.of("code", "UPSTREAM_ERROR", "message", "蒜宝暂时不可用，请稍后再试"));
                    emitter.complete();
                    return;
                }

                StringBuilder fullContent = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) break;
                            try {
                                Map<String, Object> chunk = mapper.readValue(data, Map.class);
                                List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                                    if (delta != null) {
                                        Object content = delta.get("content");
                                        if (content != null && !content.toString().isEmpty()) {
                                            String text = content.toString();
                                            fullContent.append(text);
                                            sendEvent(emitter, "delta", Map.of("text", text));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("Skip unparseable SSE line: {}", data);
                            }
                        }
                    }
                }

                // 解析推荐链接
                List<Map<String, String>> recommendations = extractRecommendations(fullContent.toString());
                if (!recommendations.isEmpty()) {
                    sendEvent(emitter, "recommendations", Map.of("items", recommendations));
                }

                sendEvent(emitter, "done", Map.of());
                emitter.complete();
                incrementRateCounters(ip, deviceId);

            } catch (Exception e) {
                log.error("Assistant stream error", e);
                sendEvent(emitter, "error", Map.of("code", "INTERNAL_ERROR", "message", "蒜宝暂时不可用，请稍后再试"));
                emitter.complete();
            }
        }, "assistant-chat").start();

        return emitter;
    }

    /** 从回复中提取推荐链接。 */
    private List<Map<String, String>> extractRecommendations(String content) {
        List<Map<String, String>> items = new ArrayList<>();
        List<String> knownPaths = List.of(
                "/kai", "/features", "/download", "/console", "/console/wallet",
                "/console/logs", "/login", "/register", "/"
        );
        Map<String, String> pathLabels = Map.of(
                "/kai", "了解 KAI 期算",
                "/features", "查看产品特性",
                "/download", "下载 KOD蒜粒",
                "/console", "进入控制台",
                "/console/wallet", "充值钱包",
                "/console/logs", "查看日志",
                "/login", "登录账号",
                "/register", "注册账号",
                "/", "回到首页"
        );

        for (String path : knownPaths) {
            if (content.contains(path) && items.size() < 3) {
                items.add(Map.of(
                        "label", pathLabels.getOrDefault(path, "查看页面"),
                        "path", path
                ));
            }
        }
        return items;
    }

    /** 速率限制检查，返回 null 表示通过，否则返回错误消息。 */
    private String checkRateLimit(String ip, String deviceId) {
        try {
            String minKey = REDIS_PREFIX + "rate:min:" + ip;
            Long minCount = redis.opsForValue().increment(minKey);
            if (minCount != null && minCount == 1) redis.expire(minKey, 60, TimeUnit.SECONDS);
            if (minCount != null && minCount > props.getMaxPerMinutePerIp())
                return "请求太频繁，请稍后再试";

            String dayIpKey = REDIS_PREFIX + "rate:day:ip:" + ip;
            Long dayIpCount = redis.opsForValue().increment(dayIpKey);
            if (dayIpCount != null && dayIpCount == 1)
                redis.expire(dayIpKey, 24, TimeUnit.HOURS);
            if (dayIpCount != null && dayIpCount > props.getMaxDailyPerIp())
                return "今日请求已达上限，请明天再试";

            if (deviceId != null && !deviceId.isEmpty()) {
                String dayDevKey = REDIS_PREFIX + "rate:day:dev:" + deviceId;
                Long dayDevCount = redis.opsForValue().increment(dayDevKey);
                if (dayDevCount != null && dayDevCount == 1)
                    redis.expire(dayDevKey, 24, TimeUnit.HOURS);
                if (dayDevCount != null && dayDevCount > props.getMaxDailyPerDevice())
                    return "今日请求已达上限，请明天再试";
            }

            return null;
        } catch (Exception e) {
            log.warn("Rate limit check failed, allowing request: {}", e.getMessage());
            return null;
        }
    }

    /** 递增速率计数器（请求完成后调用）。 */
    private void incrementRateCounters(String ip, String deviceId) {
        // 计数器已在 checkRateLimit 中递增，这里无需重复操作
    }

    /** 发送 SSE 事件。 */
    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.debug("SSE send failed for event {}: {}", event, e.getMessage());
        }
    }

    /** 解析超时字符串为毫秒。 */
    private int parseTimeout(String timeout) {
        if (timeout == null || timeout.isEmpty()) return 30_000;
        timeout = timeout.trim().toLowerCase();
        try {
            if (timeout.endsWith("ms")) return Integer.parseInt(timeout.replace("ms", ""));
            if (timeout.endsWith("s")) return Integer.parseInt(timeout.replace("s", "")) * 1000;
            if (timeout.endsWith("m")) return Integer.parseInt(timeout.replace("m", "")) * 60_000;
            return Integer.parseInt(timeout) * 1000;
        } catch (NumberFormatException e) {
            return 30_000;
        }
    }
}
