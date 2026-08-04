package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对标 new-api FlowQuotaData 的响应格式。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowQuotaDataResponse {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("node_name")
    private String nodeName;

    @JsonProperty("token_id")
    private Integer tokenId;

    @JsonProperty("token_name")
    private String tokenName;

    @JsonProperty("use_group")
    private String useGroup;

    @JsonProperty("channel_id")
    private Integer channelId;

    @JsonProperty("channel_name")
    private String channelName;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("token_used")
    private Integer tokenUsed;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("quota")
    private Integer quota;
}
