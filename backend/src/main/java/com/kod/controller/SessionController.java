package com.kod.controller;

import com.kod.common.Result;
import com.kod.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 客户端会话接口：API Key 选择/释放、余额查询。
 */
@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * 选择/切换 API Key。
     */
    @PostMapping("/select-key")
    public Result<Map<String, Object>> selectKey(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Long> body) {
        Long userId = sessionService.parseUserIdFromHeader(authorization);
        Long stationId = body.get("station_id");
        Long apiKeyId = body.get("api_key_id");
        if (stationId == null || apiKeyId == null) {
            return Result.fail(400, "station_id 和 api_key_id 不能为空");
        }
        log.info("选择 Key，userId={}, stationId={}, apiKeyId={}", userId, stationId, apiKeyId);
        return Result.ok(sessionService.selectKey(userId, stationId, apiKeyId));
    }

    /**
     * 释放当前占用的 API Key。
     */
    @PostMapping("/release-key")
    public Result<Map<String, Object>> releaseKey(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = sessionService.parseUserIdFromHeader(authorization);
        log.info("释放 Key，userId={}", userId);
        return Result.ok(sessionService.releaseKey(userId));
    }

    /**
     * 查询余额与能否发起对话。
     */
    @GetMapping("/balance")
    public Result<Map<String, Object>> getBalance(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = sessionService.parseUserIdFromHeader(authorization);
        return Result.ok(sessionService.getBalance(userId));
    }
}
