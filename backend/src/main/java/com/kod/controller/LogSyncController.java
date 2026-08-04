package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.service.LogSyncService;
import com.kod.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志同步与聊天代理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class LogSyncController {

    private final LogSyncService logSyncService;
    private final SessionService sessionService;

    /**
     * 手动触发日志同步（用户点击发送后调用）。
     */
    @PostMapping("/sync/start")
    public Result<Map<String, Object>> startSync(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = sessionService.parseUserIdFromHeader(authorization);
        logSyncService.onUserClickSend(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("running", true);
        data.put("message", "日志同步已启动");
        return Result.ok(data);
    }

    /**
     * 停止日志同步（用户关闭对话页面时调用）。
     */
    @PostMapping("/sync/stop")
    public Result<Map<String, Object>> stopSync(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        sessionService.parseUserIdFromHeader(authorization); // 校验 token
        logSyncService.onUserClose();
        Map<String, Object> data = new HashMap<>();
        data.put("running", false);
        data.put("message", "日志同步已停止");
        return Result.ok(data);
    }

    /**
     * 查询同步状态。
     */
    @GetMapping("/sync/status")
    public Result<Map<String, Object>> syncStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("syncing", logSyncService.isRunning());
        return Result.ok(data);
    }
}
