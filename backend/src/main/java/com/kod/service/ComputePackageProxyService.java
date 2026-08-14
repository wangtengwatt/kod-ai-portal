package com.kod.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kod.common.BizException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** OpenAI 兼容的套餐专属代理；零售站真实 Key 永不返回给买家。 */
@Service
@RequiredArgsConstructor
public class ComputePackageProxyService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper objectMapper;
    private final ComputePackageService packages;

    public void proxy(String endpoint, String authorization, String rawBody, HttpServletResponse servletResponse) {
        ComputePackageService.ProxyAccess access = null;
        int upstreamStatus = 0;
        try {
            ObjectNode body = parseBody(rawBody);
            String requestedModel = body.path("model").asText("").trim();
            if (requestedModel.isEmpty()) throw new BizException(400, "请求缺少 model 字段");
            boolean stream = body.path("stream").asBoolean(false);
            if (stream && "/chat/completions".equals(endpoint)) {
                ObjectNode streamOptions = body.withObject("/stream_options");
                streamOptions.put("include_usage", true);
            }
            access = packages.acquireProxy(bearer(authorization), endpoint, requestedModel);
            HttpRequest upstreamRequest = HttpRequest.newBuilder()
                    .uri(URI.create(upstreamUrl(access.stationUrl(), endpoint)))
                    .timeout(Duration.ofMinutes(10))
                    .header("Authorization", "Bearer " + access.upstreamApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", stream ? "text/event-stream, application/json" : "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<InputStream> upstream = HTTP.send(upstreamRequest, HttpResponse.BodyHandlers.ofInputStream());
            upstreamStatus = upstream.statusCode();
            servletResponse.setStatus(upstreamStatus);
            servletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            servletResponse.setContentType(upstream.headers().firstValue("content-type")
                    .orElse(stream ? "text/event-stream" : "application/json"));
            servletResponse.setHeader("X-KOD-Proxy-Request-Id", access.requestId());

            if (upstreamStatus < 200 || upstreamStatus >= 300) {
                upstream.body().transferTo(servletResponse.getOutputStream());
                servletResponse.flushBuffer();
                packages.failProxy(access, upstreamStatus, "上游返回 HTTP " + upstreamStatus, false);
                return;
            }

            Usage usage = stream
                    ? streamAndReadUsage(upstream.body(), servletResponse)
                    : copyJsonAndReadUsage(upstream.body(), servletResponse);
            if (usage.found()) {
                packages.completeProxy(access, usage.promptTokens(), usage.completionTokens(), upstreamStatus);
            } else {
                packages.failProxy(access, upstreamStatus, "上游响应未返回可核验 usage", true);
            }
        } catch (BizException e) {
            if (access != null) {
                packages.failProxy(access, upstreamStatus, e.getMessage(), servletResponse.isCommitted());
            }
            writeErrorIfPossible(servletResponse, e.getCode(), e.getMessage());
        } catch (Exception e) {
            if (access != null) {
                packages.failProxy(access, upstreamStatus, "代理调用异常：" + e.getMessage(),
                        servletResponse.isCommitted());
            }
            writeErrorIfPossible(servletResponse, 502, "平台代理调用失败");
        }
    }

    private Usage streamAndReadUsage(InputStream body, HttpServletResponse response) throws Exception {
        Usage latest = Usage.missing();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
                response.getOutputStream().flush();
                String candidate = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                if (candidate.isEmpty() || "[DONE]".equals(candidate) || !candidate.startsWith("{")) continue;
                try {
                    Usage parsed = usage(objectMapper.readTree(candidate));
                    if (parsed.found()) latest = parsed;
                } catch (Exception ignored) {
                    // 普通增量数据块没有 usage，继续等待最后一个可核验数据块。
                }
            }
        }
        response.flushBuffer();
        return latest;
    }

    private Usage copyJsonAndReadUsage(InputStream body, HttpServletResponse response) throws Exception {
        byte[] payload = body.readAllBytes();
        response.getOutputStream().write(payload);
        response.flushBuffer();
        try {
            return usage(objectMapper.readTree(payload));
        } catch (Exception ignored) {
            return Usage.missing();
        }
    }

    private Usage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) usage = root.path("response").path("usage");
        if (usage.isMissingNode() || usage.isNull()) return Usage.missing();
        JsonNode prompt = usage.has("prompt_tokens") ? usage.get("prompt_tokens") : usage.get("input_tokens");
        JsonNode completion = usage.has("completion_tokens")
                ? usage.get("completion_tokens") : usage.get("output_tokens");
        if (prompt == null || completion == null || !prompt.canConvertToLong() || !completion.canConvertToLong()) {
            return Usage.missing();
        }
        return new Usage(true, Math.max(0, prompt.asLong()), Math.max(0, completion.asLong()));
    }

    private ObjectNode parseBody(String rawBody) {
        try {
            JsonNode body = objectMapper.readTree(rawBody);
            if (!(body instanceof ObjectNode object)) throw new BizException(400, "请求体必须是 JSON 对象");
            return object;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "请求体不是有效 JSON");
        }
    }

    private static String bearer(String authorization) {
        String value = authorization == null ? "" : authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) return value.substring(7).trim();
        throw new BizException(401, "请使用 Authorization: Bearer <套餐Key>");
    }

    private static String upstreamUrl(String stationUrl, String endpoint) {
        String base = stationUrl.trim().replaceAll("/+$", "");
        if (!base.endsWith("/v1")) base += "/v1";
        return base + endpoint;
    }

    private void writeErrorIfPossible(HttpServletResponse response, int status, String message) {
        if (response.isCommitted()) return;
        try {
            response.reset();
            response.setStatus(status >= 400 && status <= 599 ? status : 500);
            response.setContentType("application/json;charset=UTF-8");
            ObjectNode error = objectMapper.createObjectNode();
            error.putObject("error").put("message", message).put("type", "kod_compute_proxy_error");
            response.getOutputStream().write(objectMapper.writeValueAsBytes(error));
            response.flushBuffer();
        } catch (Exception ignored) {
            // 响应通道已经断开时只能依靠服务端流水恢复 in_flight 状态。
        }
    }

    private record Usage(boolean found, long promptTokens, long completionTokens) {
        private static Usage missing() {
            return new Usage(false, 0, 0);
        }
    }
}
