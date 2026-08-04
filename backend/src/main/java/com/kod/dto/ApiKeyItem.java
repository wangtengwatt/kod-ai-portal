package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 列表项。
 */
@Data
@AllArgsConstructor
public class ApiKeyItem {

    /** API Key ID。 */
    @JsonProperty("id")
    private Long id;

    /** 所属中转站ID。 */
    @JsonProperty("station_id")
    private Long stationId;

    /** API Key 值。 */
    @JsonProperty("api_key")
    private String apiKey;

    /** 状态：0=空闲(绿点) 1=占用中(红点)。 */
    @JsonProperty("status")
    private Integer status;

    /** 创建时间。 */
    @JsonProperty("create_time")
    private LocalDateTime createTime;
}
