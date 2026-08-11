package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单笔订单详情响应（对应 orders 表全部字段）。
 */
@Data
public class OrderStatusResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("payment_provider")
    private String paymentProvider;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("money")
    private BigDecimal money;

    @JsonProperty("order_no")
    private String orderNo;

    @JsonProperty("status")
    private String status;

    @JsonProperty("coupon_id")
    private Long couponId;

    @JsonProperty("create_time")
    private Long createTime;

    @JsonProperty("update_time")
    private Long updateTime;

    @JsonProperty("complete_time")
    private Long completeTime;
}
