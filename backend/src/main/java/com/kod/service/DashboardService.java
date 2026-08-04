package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kod.dto.*;
import com.kod.entity.DashboardHourly;
import com.kod.entity.DashboardModelSummary;
import com.kod.entity.User;
import com.kod.mapper.DashboardHourlyMapper;
import com.kod.mapper.DashboardModelSummaryMapper;
import com.kod.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 看板服务。
 *
 * <p>对标 new-api 的模型用量数据接口，数据来源为
 * {@code dashboard_hourly} 和 {@code dashboard_model_summary} 两张聚合表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardModelSummaryMapper summaryMapper;
    private final DashboardHourlyMapper hourlyMapper;
    private final UserMapper userMapper;

    // -------------------------------------------------------
    // 对标 new-api GET /api/data/self
    // -------------------------------------------------------

    /**
     * 按时间范围查询当前用户的用量数据。
     *
     * <p>对标 new-api 的 {@code GetQuotaDataByUserId}，
     * 从 dashboard_hourly 查询并聚合为 new-api QuotaData 格式。</p>
     *
     * @param userId         用户 ID
     * @param startTimestamp 起始时间（Unix 秒）
     * @param endTimestamp   结束时间（Unix 秒）
     * @return QuotaData 列表（按 model_name + created_at 分组）
     */
    public List<QuotaDataResponse> getQuotaData(Long userId, long startTimestamp, long endTimestamp) {
        User user = userMapper.selectById(userId);
        String username = user != null ? user.getEmail() : "";

        List<DashboardHourly> rows = hourlyMapper.selectList(
                Wrappers.<DashboardHourly>lambdaQuery()
                        .eq(DashboardHourly::getUserId, userId)
                        .ge(DashboardHourly::getHourBucket, startTimestamp)
                        .le(DashboardHourly::getHourBucket, endTimestamp)
                        .orderByAsc(DashboardHourly::getHourBucket));

        // 聚合：按 model_name + hour_bucket 合并（跨 channel 求和）
        Map<String, QuotaDataResponse> map = new LinkedHashMap<>();
        int seq = 0;
        for (DashboardHourly row : rows) {
            String key = row.getModelName() + "\0" + row.getHourBucket();
            QuotaDataResponse item = map.get(key);
            if (item == null) {
                item = new QuotaDataResponse();
                item.setId(++seq);
                item.setUserId(userId);
                item.setUsername(username);
                item.setModelName(row.getModelName());
                item.setCreatedAt(row.getHourBucket());
                item.setTokenUsed(0);
                item.setCount(0);
                item.setQuota(0);
                map.put(key, item);
            }
            item.setTokenUsed(item.getTokenUsed() + nvl(row.getTokenUsed()));
            item.setCount(item.getCount() + nvl(row.getRequestCount()));
            item.setQuota(item.getQuota() + nvl(row.getQuota()));
        }

        return new ArrayList<>(map.values());
    }

    // -------------------------------------------------------
    // 对标 new-api GET /api/data/flow/self
    // -------------------------------------------------------

    /**
     * 按时间范围查询当前用户的流量数据。
     *
     * <p>对标 new-api 的 {@code GetFlowQuotaData}（self 模式），
     * 保留 channel_id 维度不做跨 channel 合并。</p>
     */
    public List<FlowQuotaDataResponse> getFlowQuotaData(Long userId, long startTimestamp, long endTimestamp) {
        User user = userMapper.selectById(userId);
        String username = user != null ? user.getEmail() : "";

        List<DashboardHourly> rows = hourlyMapper.selectList(
                Wrappers.<DashboardHourly>lambdaQuery()
                        .eq(DashboardHourly::getUserId, userId)
                        .ge(DashboardHourly::getHourBucket, startTimestamp)
                        .le(DashboardHourly::getHourBucket, endTimestamp)
                        .orderByAsc(DashboardHourly::getHourBucket));

        return rows.stream().map(row -> {
            FlowQuotaDataResponse item = new FlowQuotaDataResponse();
            item.setUserId(userId);
            item.setUsername(username);
            item.setModelName(row.getModelName());
            item.setChannelId(row.getChannelId());
            item.setChannelName("Channel " + row.getChannelId());
            item.setTokenUsed(nvl(row.getTokenUsed()));
            item.setCount(nvl(row.getRequestCount()));
            item.setQuota(nvl(row.getQuota()));
            item.setNodeName("");
            item.setUseGroup("");
            item.setTokenName("");
            return item;
        }).collect(Collectors.toList());
    }

    // -------------------------------------------------------
    // 模型汇总（原有）
    // -------------------------------------------------------

    public List<DashboardSummaryResponse> getSummary(Long userId) {
        List<DashboardModelSummary> rows = summaryMapper.selectList(
                Wrappers.<DashboardModelSummary>lambdaQuery()
                        .eq(DashboardModelSummary::getUserId, userId)
                        .orderByDesc(DashboardModelSummary::getTotalTokens));

        return rows.stream().map(row -> new DashboardSummaryResponse(
                row.getModelName(),
                row.getTotalRequests(),
                row.getTotalQuota(),
                row.getTotalPrompt(),
                row.getTotalCompletion(),
                row.getTotalTokens(),
                row.getTotalUseTime(),
                row.getTotalStream(),
                row.getLastRequestAt()
        )).collect(Collectors.toList());
    }

    // -------------------------------------------------------
    // 小时趋势（原有）
    // -------------------------------------------------------

    public List<HourlyTrendResponse> getHourlyTrend(Long userId, int hours) {
        long now = System.currentTimeMillis() / 1000;
        long since = now - (long) hours * 3600;

        List<DashboardHourly> rows = hourlyMapper.selectList(
                Wrappers.<DashboardHourly>lambdaQuery()
                        .eq(DashboardHourly::getUserId, userId)
                        .ge(DashboardHourly::getHourBucket, since)
                        .orderByAsc(DashboardHourly::getHourBucket));

        Map<Long, HourlyTrendResponse> map = new LinkedHashMap<>();
        for (DashboardHourly row : rows) {
            HourlyTrendResponse item = map.computeIfAbsent(row.getHourBucket(),
                    k -> new HourlyTrendResponse(k, 0, 0, 0, 0));
            item.setRequestCount(item.getRequestCount() + nvl(row.getRequestCount()));
            item.setQuota(item.getQuota() + nvl(row.getQuota()));
            item.setTokenUsed(item.getTokenUsed() + nvl(row.getTokenUsed()));
            item.setStreamCount(item.getStreamCount() + nvl(row.getStreamCount()));
        }

        return new ArrayList<>(map.values());
    }

    // -------------------------------------------------------
    // 工具
    // -------------------------------------------------------

    private static int nvl(Integer v) {
        return v != null ? v : 0;
    }
}
