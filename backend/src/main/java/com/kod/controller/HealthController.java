package com.kod.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器。
 *
 * <p>提供 {@code GET /api/health} 探活接口，供负载均衡 / 监控系统使用，不依赖数据库。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 应用名称，来自配置 {@code spring.application.name}。
     */
    @Value("${spring.application.name:kod-portal-backend}")
    private String appName;

    /**
     * 健康检查接口。
     *
     * @return 包含服务状态、应用名与当前时间的响应体，HTTP 状态固定为 200
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        log.debug("收到健康检查请求");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("app", appName);
        result.put("timestamp", LocalDateTime.now());
        return result;
    }
}
