package com.kod.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 计算金额请求。
 */
@Data
public class AmountRequest {

    /** 充值面额。 */
    @NotNull(message = "金额不能为空")
    @Min(value = 1, message = "金额至少为1元")
    private Integer amount;
}
