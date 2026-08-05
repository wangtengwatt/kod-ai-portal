package com.kod.controller;

import com.kod.service.AssistantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 蒜宝助手控制器 —— 提供 {@code POST /api/public/assistant/chat} SSE 流式对话接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * 流式对话（SSE）。
     *
     * @param body      请求体，包含 sessionId, deviceId, currentPath, messages
     * @param request   HTTP 请求（用于获取客户端 IP 和设备 ID）
     * @return SseEmitter 流式响应
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String deviceId = request.getHeader("X-Assistant-Device-Id");
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = (String) body.getOrDefault("deviceId", "");
        }

        String currentPath = (String) body.getOrDefault("currentPath", "");
        String ip = getClientIp(request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");

        log.debug("Assistant chat request: device={}, path={}, msgCount={}, ip={}",
                deviceId, currentPath, messages != null ? messages.size() : 0, ip);

        return assistantService.chat(deviceId, currentPath, messages, ip);
    }

    /** 获取客户端真实 IP。 */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
