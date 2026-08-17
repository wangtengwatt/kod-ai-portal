package com.kod.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 算力中心部署配置。
 *
 * <p>管理员邮箱、交付信息加密密钥等敏感配置只允许从环境变量注入，
 * 不在数据库旧表或源码中写死。</p>
 */
@Getter
@Component
public class ComputeCenterProperties {

    private final Set<String> adminEmails;
    private final Set<String> kaiStationDomains;
    private final BigDecimal defaultCardHourCnyRate;
    private final BigDecimal defaultTransferReviewThreshold;
    private final String deliverySecret;
    private final Path privateStorageDirectory;
    private final boolean schemaInitEnabled;
    private final boolean testIdentityEnabled;
    private final boolean demoSeedEnabled;
    private final String demoSupplierEmail;
    private final String demoBuyerEmail;
    private final String proxyBaseUrl;
    private final BigDecimal defaultUsdCnyRate;

    public ComputeCenterProperties(
            @Value("${kod.compute.admin-emails:}") String adminEmails,
            @Value("${kod.compute.kai-station-domains:kai.com}") String kaiStationDomains,
            @Value("${kod.compute.card-hour-cny-rate:1.002}") BigDecimal defaultCardHourCnyRate,
            @Value("${kod.compute.transfer-review-threshold:1000.000}") BigDecimal defaultTransferReviewThreshold,
            @Value("${kod.compute.delivery-secret:}") String deliverySecret,
            @Value("${kod.compute.private-storage-directory:./data/compute-private}") String privateStorageDirectory,
            @Value("${kod.compute.schema-init-enabled:false}") boolean schemaInitEnabled,
            @Value("${kod.compute.test-identity-enabled:false}") boolean testIdentityEnabled,
            @Value("${kod.compute.demo-seed-enabled:false}") boolean demoSeedEnabled,
            @Value("${kod.compute.demo-supplier-email:}") String demoSupplierEmail,
            @Value("${kod.compute.demo-buyer-email:}") String demoBuyerEmail,
            @Value("${kod.compute.proxy-base-url:http://127.0.0.1:8080/api/compute/proxy/v1}") String proxyBaseUrl,
            @Value("${kod.compute.usd-cny-rate:7.2000}") BigDecimal defaultUsdCnyRate) {
        this.adminEmails = csvSet(adminEmails);
        this.kaiStationDomains = csvSet(kaiStationDomains);
        this.defaultCardHourCnyRate = defaultCardHourCnyRate;
        this.defaultTransferReviewThreshold = defaultTransferReviewThreshold;
        this.deliverySecret = deliverySecret == null ? "" : deliverySecret.trim();
        this.privateStorageDirectory = Path.of(privateStorageDirectory).toAbsolutePath().normalize();
        this.schemaInitEnabled = schemaInitEnabled;
        this.testIdentityEnabled = testIdentityEnabled;
        this.demoSeedEnabled = demoSeedEnabled;
        this.demoSupplierEmail = demoSupplierEmail == null ? "" : demoSupplierEmail.trim().toLowerCase(Locale.ROOT);
        this.demoBuyerEmail = demoBuyerEmail == null ? "" : demoBuyerEmail.trim().toLowerCase(Locale.ROOT);
        this.proxyBaseUrl = trimTrailingSlash(proxyBaseUrl);
        this.defaultUsdCnyRate = defaultUsdCnyRate;
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> csvSet(String value) {
        return Arrays.stream(value == null ? new String[0] : value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
