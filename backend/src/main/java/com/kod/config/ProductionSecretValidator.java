package com.kod.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ProductionSecretValidator implements ApplicationRunner {
    private final Environment environment;
    private final JwtProperties jwt;
    private final SyncProperties sync;
    private final CloudSandboxProperties cloud;
    private final StoreKitProperties storeKit;
    private final IdentityProperties identity;
    private final MediaStorageProperties mediaStorage;

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) return;
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(jwt.getSecret()) || jwt.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) missing.add("JWT_SECRET (>=32 bytes)");
        if (!StringUtils.hasText(environment.getProperty("spring.datasource.password"))) missing.add("MYSQL_PASSWORD");
        if (!StringUtils.hasText(environment.getProperty("spring.mail.username"))) missing.add("MAIL_USERNAME");
        if (!StringUtils.hasText(environment.getProperty("spring.mail.password"))) missing.add("MAIL_PASSWORD");
        if (sync.isEnabled() && !validVaultKey(sync.getVaultMasterKey())) missing.add("KOD_SYNC_VAULT_MASTER_KEY (Base64 32 bytes)");
        if (sync.isEnabled()) {
            if (!mediaStorage.isEnabled()) missing.add("KOD_MEDIA_STORAGE_ENABLED=true");
            if (!StringUtils.hasText(mediaStorage.getBucket())) missing.add("KOD_MEDIA_STORAGE_BUCKET");
            if (StringUtils.hasText(mediaStorage.getEndpoint())
                    && !mediaStorage.getEndpoint().trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
                missing.add("KOD_MEDIA_STORAGE_ENDPOINT (HTTPS)");
            }
            boolean hasAccessKey = StringUtils.hasText(mediaStorage.getAccessKey());
            boolean hasSecretKey = StringUtils.hasText(mediaStorage.getSecretKey());
            if (hasAccessKey != hasSecretKey) missing.add("KOD_MEDIA_STORAGE_ACCESS_KEY and KOD_MEDIA_STORAGE_SECRET_KEY together");
        }
        if (cloud.isEnabled() && (!StringUtils.hasText(cloud.getWorkerBootstrapSecret())
                || cloud.getWorkerBootstrapSecret().getBytes(StandardCharsets.UTF_8).length < 32)) {
            missing.add("KOD_WORKER_BOOTSTRAP_SECRET (>=32 bytes)");
        }
        if (identity.isEnabled() && !StringUtils.hasText(identity.getIosClientId())) {
            missing.add("KOD_IDENTITY_IOS_CLIENT_ID");
        }
        if (storeKit.isEnabled()) {
            if (storeKit.getRootCertificatePaths().isEmpty()) missing.add("KOD_STOREKIT_ROOT_CERTIFICATE_PATHS");
            if (storeKit.getAppAppleId() == null) missing.add("KOD_STOREKIT_APP_APPLE_ID");
            if (storeKit.isAllowXcodeTransactions()) missing.add("KOD_STOREKIT_ALLOW_XCODE_TRANSACTIONS=false");
            if (storeKit.getSubscriptionProductIds().isEmpty() && storeKit.getConsumableCredits().isEmpty()) {
                missing.add("StoreKit product allowlist");
            }
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Production secrets/configuration missing: " + String.join(", ", missing));
    }

    private boolean validVaultKey(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            return Base64.getDecoder().decode(value).length == 32;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
