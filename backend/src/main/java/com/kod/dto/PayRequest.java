package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起支付请求。
 */
@Data
public class PayRequest {

    /** 充值面额。 */
    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额至少为1元")
    private Integer amount;

    /** 支付方式：alipay / wxpay。 */
    @NotBlank(message = "支付方式不能为空")
    @JsonProperty("payment_method")
    private String paymentMethod;
}
