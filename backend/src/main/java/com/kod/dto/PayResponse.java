package com.kod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 支付响应。
 */
@Data
@AllArgsConstructor
public class PayResponse {

    /** 订单号。 */
    private String orderNo;

    /** 支付跳转URL。 */
    private String paymentUrl;
}
