package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 充值配置信息响应（对标 new-api GET /api/user/topup/info）。
 *
 * <p>字段名使用 snake_case，与 new-api 保持一致。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopUpInfoResponse {

    @JsonProperty("enable_online_topup")
    private boolean enableOnlineTopup;

    @JsonProperty("enable_stripe_topup")
    private boolean enableStripeTopup;

    @JsonProperty("enable_creem_topup")
    private boolean enableCreemTopup;

    @JsonProperty("enable_waffo_topup")
    private boolean enableWaffoTopup;

    @JsonProperty("enable_waffo_pancake_topup")
    private boolean enableWaffoPancakeTopup;

    @JsonProperty("payment_compliance_confirmed")
    private boolean paymentComplianceConfirmed;

    @JsonProperty("payment_compliance_terms_version")
    private String paymentComplianceTermsVersion;

    @JsonProperty("min_topup")
    private int minTopup;

    @JsonProperty("stripe_min_topup")
    private int stripeMinTopup;

    @JsonProperty("waffo_min_topup")
    private int waffoMinTopup;

    @JsonProperty("waffo_pancake_min_topup")
    private int waffoPancakeMinTopup;

    @JsonProperty("amount_options")
    private List<Integer> amountOptions;

    @JsonProperty("discount")
    private Map<Integer, Double> discount;

    @JsonProperty("pay_methods")
    private List<PayMethod> payMethods;

    @JsonProperty("topup_link")
    private String topupLink;

    @JsonProperty("waffo_pay_methods")
    private List<PayMethod> waffoPayMethods;

    @JsonProperty("creem_products")
    private String creemProducts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayMethod {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type;

        @JsonProperty("color")
        private String color;

        @JsonProperty("min_topup")
        private String minTopup;
    }
}
