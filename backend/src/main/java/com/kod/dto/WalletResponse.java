package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包余额响应。
 */
@Data
@AllArgsConstructor
public class WalletResponse {

    @JsonProperty("balance")
    private BigDecimal balance;

    @JsonProperty("historical_consumption")
    private BigDecimal historicalConsumption;
}
