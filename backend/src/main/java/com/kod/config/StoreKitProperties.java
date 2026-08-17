package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "kod.storekit")
public class StoreKitProperties {
    private boolean enabled = false;
    private boolean schemaInitEnabled = true;
    private String bundleId = "com.kai.kod";
    private Long appAppleId;
    private boolean allowXcodeTransactions = false;
    private List<String> rootCertificatePaths = new ArrayList<>();
    private List<String> subscriptionProductIds = new ArrayList<>();
    private Map<String, Long> consumableCredits = new HashMap<>();
    /** Comma-separated product=credits entries for container environment configuration. */
    private String consumableCreditsSpec = "";

    public Map<String, Long> getConsumableCredits() {
        if (!StringUtils.hasText(consumableCreditsSpec)) return consumableCredits;
        for (String entry : consumableCreditsSpec.split(",")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length != 2 || !StringUtils.hasText(pair[0])) {
                throw new IllegalStateException("Invalid StoreKit consumable credit mapping");
            }
            try {
                long credits = Long.parseLong(pair[1].trim());
                if (credits <= 0) throw new NumberFormatException();
                consumableCredits.put(pair[0].trim(), credits);
            } catch (NumberFormatException error) {
                throw new IllegalStateException("Invalid StoreKit consumable credit mapping", error);
            }
        }
        return consumableCredits;
    }
}
