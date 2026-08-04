package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.*;
import com.kod.service.DashboardService;
import com.kod.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 看板接口。
 *
 * <p>对标 new-api 的数据看板 API，提供 /api/data/self、/api/data/flow/self
 * 以及 /api/dashboard/summary、/api/dashboard/hourly 等端点。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final JwtUtil jwtUtil;

    // ================================================================
    // 对标 new-api /api/data/*
    // ================================================================

    /**
     * 对标 new-api GET /api/data/self。
     * <p>返回当前用户在指定时间范围内的 quota_data 聚合数据。</p>
     */
    @GetMapping("/api/data/self")
    public Result<List<QuotaDataResponse>> getDataSelf(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("start_timestamp") long startTimestamp,
            @RequestParam("end_timestamp") long endTimestamp) {
        Long userId = parseUserId(authorization);
        // 时间跨度限制 30 天（与 new-api 一致）
        if (endTimestamp - startTimestamp > 2592000) {
            return Result.fail(400, "时间跨度不能超过 1 个月");
        }
        log.info("GET /api/data/self, userId={}, start={}, end={}", userId, startTimestamp, endTimestamp);
        return Result.ok(dashboardService.getQuotaData(userId, startTimestamp, endTimestamp));
    }

    /**
     * 对标 new-api GET /api/data/flow/self。
     */
    @GetMapping("/api/data/flow/self")
    public Result<List<FlowQuotaDataResponse>> getDataFlowSelf(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("start_timestamp") long startTimestamp,
            @RequestParam("end_timestamp") long endTimestamp) {
        Long userId = parseUserId(authorization);
        if (endTimestamp - startTimestamp > 2592000) {
            return Result.fail(400, "时间跨度不能超过 1 个月");
        }
        log.info("GET /api/data/flow/self, userId={}, start={}, end={}", userId, startTimestamp, endTimestamp);
        return Result.ok(dashboardService.getFlowQuotaData(userId, startTimestamp, endTimestamp));
    }

    // ================================================================
    // /api/dashboard/* 端点（原有）
    // ================================================================

    @GetMapping("/api/dashboard/summary")
    public Result<List<DashboardSummaryResponse>> getSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = parseUserId(authorization);
        log.info("查询看板汇总，userId={}", userId);
        return Result.ok(dashboardService.getSummary(userId));
    }

    @GetMapping("/api/dashboard/hourly")
    public Result<List<HourlyTrendResponse>> getHourly(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "24") int hours) {
        Long userId = parseUserId(authorization);
        int safeHours = Math.max(1, Math.min(hours, 720));
        log.info("查询看板趋势，userId={}, hours={}", userId, safeHours);
        return Result.ok(dashboardService.getHourlyTrend(userId, safeHours));
    }

    // ================================================================
    // 工具
    // ================================================================

    private Long parseUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }
}
