package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值记录单条。
 */
@Data
public class TopUpItemResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("money")
    private BigDecimal money;

    @JsonProperty("trade_no")
    private String tradeNo;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("payment_provider")
    private String paymentProvider;

    @JsonProperty("create_time")
    private Long createTime;

    @JsonProperty("complete_time")
    private Long completeTime;

    @JsonProperty("status")
    private String status;
}
