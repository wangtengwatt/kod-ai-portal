package com.kod.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器。
 *
 * <p>提供 {@code GET /api/health} 探活接口，供负载均衡 / 监控系统使用。
 * 附带 Redis 连通性信息（尽力探测，不影响接口返回 200）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    /**
     * 应用名称，来自配置 {@code spring.application.name}。
     */
    @Value("${spring.application.name:kod-portal-backend}")
    private String appName;

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 健康检查接口。
     *
     * @return 包含服务状态、应用名、当前时间与 Redis 连通性的响应体，HTTP 状态固定为 200
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        log.debug("收到健康检查请求");
        Map<String, Object> result = new LinkedHashMap<>();
        String databaseStatus = pingDatabase();
        String redisStatus = pingRedis();
        result.put("status", "UP".equals(databaseStatus) && "UP".equals(redisStatus) ? "UP" : "DEGRADED");
        result.put("app", appName);
        result.put("database", databaseStatus);
        result.put("redis", redisStatus);
        result.put("timestamp", LocalDateTime.now());
        return result;
    }

    private String pingDatabase() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(value) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("数据库探测失败：{}", e.getMessage());
            return "DOWN";
        }
    }

    /**
     * 尽力探测 Redis 连通性。
     *
     * @return "UP"（可用）或 "DOWN"（不可用）
     */
    private String pingRedis() {
        try {
            String pong = stringRedisTemplate.execute(connection -> connection.ping(), true);
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Redis 探测失败：{}", e.getMessage());
            return "DOWN";
        }
    }
}
