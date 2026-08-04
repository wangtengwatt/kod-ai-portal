package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对标 new-api QuotaData 的响应格式。
 * 数据来源：dashboard_hourly 表按 (user_id, model_name, hour_bucket) 聚合。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotaDataResponse {

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("created_at")
    private Long createdAt;

    @JsonProperty("token_used")
    private Integer tokenUsed;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("quota")
    private Integer quota;
}
