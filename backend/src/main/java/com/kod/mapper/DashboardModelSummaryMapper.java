package com.kod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kod.entity.DashboardModelSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DashboardModelSummaryMapper extends BaseMapper<DashboardModelSummary> {

    @Update("INSERT INTO dashboard_model_summary (user_id, model_name, total_requests, total_quota, "
            + "total_prompt, total_completion, total_tokens, total_use_time, total_stream, last_request_at) "
            + "SELECT user_id, model_name, COUNT(*), SUM(quota), "
            + "SUM(prompt_tokens), SUM(completion_tokens), "
            + "SUM(prompt_tokens + completion_tokens), SUM(use_time), "
            + "SUM(CASE WHEN is_stream = 1 THEN 1 ELSE 0 END), MAX(created_at) "
            + "FROM api_request_logs "
            + "WHERE type = 2 AND model_name != '' AND user_id > 0 "
            + "AND synced_at > DATE_SUB(NOW(), INTERVAL 5 MINUTE) "
            + "GROUP BY user_id, model_name "
            + "ON DUPLICATE KEY UPDATE "
            + "total_requests = total_requests + VALUES(total_requests), "
            + "total_quota = total_quota + VALUES(total_quota), "
            + "total_prompt = total_prompt + VALUES(total_prompt), "
            + "total_completion = total_completion + VALUES(total_completion), "
            + "total_tokens = total_tokens + VALUES(total_tokens), "
            + "total_use_time = total_use_time + VALUES(total_use_time), "
            + "total_stream = total_stream + VALUES(total_stream), "
            + "last_request_at = GREATEST(last_request_at, VALUES(last_request_at))")
    int aggregateFromLogs();
}
