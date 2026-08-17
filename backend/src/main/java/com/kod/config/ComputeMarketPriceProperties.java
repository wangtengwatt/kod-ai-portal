package com.kod.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 第三方 GPU 行情采集配置。密钥只允许通过环境变量注入。 */
@Getter
@Component
public class ComputeMarketPriceProperties {

    private final boolean enabled;
    private final String vastApiKey;
    private final String vastSearchUrl;
    private final String akamaiProductUrl;
    private final String akamaiTypesUrl;
    private final BigDecimal defaultUsdCnyRate;
    private final long refreshMillis;
    private final long historySampleMillis;
    private final int historyRetentionDays;

    public ComputeMarketPriceProperties(
            @Value("${kod.compute.market-price-enabled:true}") boolean enabled,
            @Value("${kod.compute.vast-api-key:}") String vastApiKey,
            @Value("${kod.compute.vast-search-url:https://console.vast.ai/api/v0/bundles/}") String vastSearchUrl,
            @Value("${kod.compute.akamai-product-url:https://www.akamai.com/zh/products/gpu/nvidia}") String akamaiProductUrl,
            @Value("${kod.compute.akamai-types-url:https://api.linode.com/v4/linode/types?page_size=500}") String akamaiTypesUrl,
            @Value("${kod.compute.usd-cny-rate:7.2000}") BigDecimal defaultUsdCnyRate,
            @Value("${kod.compute.market-price-refresh-millis:5000}") long refreshMillis,
            @Value("${kod.compute.market-price-history-sample-millis:60000}") long historySampleMillis,
            @Value("${kod.compute.market-price-history-retention-days:30}") int historyRetentionDays) {
        this.enabled = enabled;
        this.vastApiKey = clean(vastApiKey);
        this.vastSearchUrl = clean(vastSearchUrl);
        this.akamaiProductUrl = clean(akamaiProductUrl);
        this.akamaiTypesUrl = clean(akamaiTypesUrl);
        this.defaultUsdCnyRate = defaultUsdCnyRate;
        this.refreshMillis = Math.max(5000, refreshMillis);
        this.historySampleMillis = Math.max(60000, historySampleMillis);
        this.historyRetentionDays = Math.max(1, historyRetentionDays);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
