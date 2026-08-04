package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 支付配置项，绑定 {@code kod.payment.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "kod.payment")
public class PaymentProperties {

    /** 是否启用在线充值（epay）。 */
    private boolean epayEnabled = true;

    /** 是否启用 Stripe 充值。 */
    private boolean stripeEnabled = false;

    /** 是否启用 Creem 充值。 */
    private boolean creemEnabled = false;

    /** 是否启用 Waffo 充值。 */
    private boolean waffoEnabled = false;

    /** 是否启用 Waffo Pancake 充值。 */
    private boolean waffoPancakeEnabled = false;

    /** 最低充值金额。 */
    private int minTopup = 1;

    /** Stripe 最低充值金额。 */
    private int stripeMinTopup = 1;

    /** Waffo 最低充值金额。 */
    private int waffoMinTopup = 1;

    /** Waffo Pancake 最低充值金额。 */
    private int waffoPancakeMinTopup = 1;

    /** 可选充值金额列表。 */
    private List<Integer> amountOptions = new ArrayList<>();

    /** 折扣配置（key:金额, value:折扣率，如 0.9 表示九折）。 */
    private Map<Integer, Double> discount;

    /** 支付合规是否已确认。 */
    private boolean complianceConfirmed = true;

    /** 支付合规条款版本。 */
    private String complianceTermsVersion = "v1";

    /** 支付方式列表。 */
    private List<PayMethodItem> payMethods = new ArrayList<>();

    /** Epay 商户ID。 */
    private String epayPid;

    /** Epay 密钥。 */
    private String epayKey;

    /** Epay Submit 地址（表单提交）。 */
    private String epayApiUrl;

    /** Epay MAPI 地址（API调用，返回JSON）。 */
    private String epayMapiUrl;

    /** Epay 回调地址。 */
    private String epayNotifyUrl;

    /** Epay 支付完成跳转地址。 */
    private String epayReturnUrl;

    @Data
    public static class PayMethodItem {
        /** 支付方式名称。 */
        private String name;
        /** 支付方式类型标识。 */
        private String type;
        /** 前端展示颜色。 */
        private String color;
        /** 该方式最低充值金额。 */
        private String minTopup;
    }
}
