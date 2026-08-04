package com.kod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kod.entity.DashboardHourly;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DashboardHourlyMapper extends BaseMapper<DashboardHourly> {

    @Update("INSERT INTO dashboard_hourly (user_id, model_name, hour_bucket, channel_id, "
            + "request_count, quota, prompt_tokens, completion_tokens, token_used, use_time, stream_count) "
            + "SELECT user_id, model_name, created_at - (created_at % 3600), channel_id, "
            + "COUNT(*), SUM(quota), SUM(prompt_tokens), SUM(completion_tokens), "
            + "SUM(prompt_tokens + completion_tokens), SUM(use_time), "
            + "SUM(CASE WHEN is_stream = 1 THEN 1 ELSE 0 END) "
            + "FROM api_request_logs "
            + "WHERE type = 2 AND model_name != '' AND user_id > 0 "
            + "AND synced_at > DATE_SUB(NOW(), INTERVAL 5 MINUTE) "
            + "GROUP BY user_id, model_name, created_at - (created_at % 3600), channel_id "
            + "ON DUPLICATE KEY UPDATE "
            + "request_count = request_count + VALUES(request_count), "
            + "quota = quota + VALUES(quota), "
            + "prompt_tokens = prompt_tokens + VALUES(prompt_tokens), "
            + "completion_tokens = completion_tokens + VALUES(completion_tokens), "
            + "token_used = token_used + VALUES(token_used), "
            + "use_time = use_time + VALUES(use_time), "
            + "stream_count = stream_count + VALUES(stream_count)")
    int aggregateFromLogs();
}
