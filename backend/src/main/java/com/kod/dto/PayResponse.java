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

    /** 支付跳转URL（手机端 WAP 支付）。 */
    private String paymentUrl;

    /** 扫码支付URL（桌面端展示二维码扫码）。 */
    private String qrcode;
}
