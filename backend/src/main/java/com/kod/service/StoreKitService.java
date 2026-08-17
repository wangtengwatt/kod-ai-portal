package com.kod.service;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.model.Type;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.kod.common.BizException;
import com.kod.config.StoreKitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreKitService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final StoreKitProperties properties;

    public Map<String, Object> account(Long userId) {
        requireEnabled();
        String accountToken = accountToken(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("appAccountToken", accountToken);
        response.put("subscriptionProductIds", properties.getSubscriptionProductIds());
        response.put("consumableProductIds", properties.getConsumableCredits().keySet());
        response.put("creditBalance", creditBalance(userId));
        response.put("activeSubscriptions", activeSubscriptions(userId));
        return response;
    }

    public Map<String, Object> submit(Long userId, String signedTransaction) {
        requireEnabled();
        Verified verified = verifyTransaction(signedTransaction);
        String expected = accountToken(userId);
        if (verified.payload().getAppAccountToken() == null ||
                !expected.equalsIgnoreCase(verified.payload().getAppAccountToken().toString())) {
            throw new BizException(403, "StoreKit 交易不属于当前 KAI 账户");
        }
        return persist(userId, verified, signedTransaction);
    }

    public Map<String, Object> notification(String signedPayload) {
        requireEnabled();
        VerifiedNotification verified = verifyNotification(signedPayload);
        ResponseBodyV2DecodedPayload payload = verified.payload();
        String uuid = payload.getNotificationUUID();
        if (!StringUtils.hasText(uuid)) throw new BizException(400, "Apple 通知缺少 notificationUUID");
        return transactions.execute(status -> {
            try {
                jdbc.update("INSERT INTO kod_ios_store_notification(notification_uuid,notification_type,environment,created_at) VALUES(?,?,?,?)",
                        uuid, payload.getRawNotificationType(), verified.environment().getValue(), System.currentTimeMillis());
            } catch (DuplicateKeyException duplicate) {
                return Map.of("accepted", true, "duplicate", true);
            }
            if (payload.getData() == null || !StringUtils.hasText(payload.getData().getSignedTransactionInfo())) {
                return Map.of("accepted", true);
            }
            String signedTransaction = payload.getData().getSignedTransactionInfo();
            Verified transaction = verifyTransactionForEnvironment(signedTransaction, verified.environment());
            Long userId = resolveOwner(transaction.payload());
            if (userId != null) persist(userId, transaction, signedTransaction);
            return Map.of("accepted", true, "linked", userId != null);
        });
    }

    private Map<String, Object> persist(Long userId, Verified verified, String signedTransaction) {
        JWSTransactionDecodedPayload payload = verified.payload();
        validateProduct(payload);
        return transactions.execute(status -> {
            String transactionId = required(payload.getTransactionId(), "transactionId");
            String originalId = required(payload.getOriginalTransactionId(), "originalTransactionId");
            String accountToken = required(payload.getAppAccountToken() == null ? null : payload.getAppAccountToken().toString(), "appAccountToken");
            String productId = required(payload.getProductId(), "productId");
            long now = System.currentTimeMillis();
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT user_id FROM kod_ios_store_transaction WHERE transaction_id=? FOR UPDATE", transactionId);
            if (!existing.isEmpty() && ((Number) existing.get(0).get("user_id")).longValue() != userId) {
                throw new BizException(409, "该 StoreKit 交易已绑定其他账户");
            }
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO kod_ios_store_transaction(transaction_id,original_transaction_id,user_id,app_account_token,product_id,product_type,environment,purchase_at,expires_at,revocation_at,signed_transaction,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        transactionId, originalId, userId, accountToken, productId,
                        payload.getType().getValue(), verified.environment().getValue(),
                        payload.getPurchaseDate(), payload.getExpiresDate(), payload.getRevocationDate(), signedTransaction, now);
            } else {
                jdbc.update("UPDATE kod_ios_store_transaction SET expires_at=?,revocation_at=?,signed_transaction=? WHERE transaction_id=?",
                        payload.getExpiresDate(), payload.getRevocationDate(), signedTransaction, transactionId);
            }
            long grant = creditGrant(payload);
            if (grant > 0) {
                insertLedger(userId, transactionId, grant, "storekit_purchase");
                if (payload.getRevocationDate() != null) {
                    insertLedger(userId, transactionId, -grant, "storekit_revocation");
                }
            }
            Map<String, Object> response = new HashMap<>();
            response.put("accepted", true);
            response.put("transactionId", transactionId);
            response.put("productId", productId);
            response.put("creditBalance", creditBalance(userId));
            response.put("activeSubscriptions", activeSubscriptions(userId));
            return response;
        });
    }

    private void insertLedger(Long userId, String transactionId, long delta, String reason) {
        try {
            jdbc.update("INSERT INTO kod_ios_credit_ledger(user_id,transaction_id,delta,reason,created_at) VALUES(?,?,?,?,?)",
                    userId, transactionId, delta, reason, System.currentTimeMillis());
        } catch (DuplicateKeyException ignored) {
            // Exactly-once grant/revocation for each Apple transaction.
        }
    }

    private long creditGrant(JWSTransactionDecodedPayload payload) {
        Long perUnit = properties.getConsumableCredits().get(payload.getProductId());
        if (perUnit == null) return 0;
        int quantity = payload.getQuantity() == null ? 1 : payload.getQuantity();
        if (quantity < 1 || quantity > 100) throw new BizException(400, "StoreKit 交易数量异常");
        try {
            return Math.multiplyExact(perUnit, quantity);
        } catch (ArithmeticException overflow) {
            throw new BizException(400, "StoreKit 权益数量溢出");
        }
    }

    private void validateProduct(JWSTransactionDecodedPayload payload) {
        String productId = required(payload.getProductId(), "productId");
        if (properties.getSubscriptionProductIds().contains(productId)) {
            if (payload.getType() != Type.AUTO_RENEWABLE_SUBSCRIPTION) {
                throw new BizException(400, "订阅商品类型与 App Store 配置不一致");
            }
            return;
        }
        if (properties.getConsumableCredits().containsKey(productId)) {
            if (payload.getType() != Type.CONSUMABLE) {
                throw new BizException(400, "点数商品类型与 App Store 配置不一致");
            }
            return;
        }
        throw new BizException(400, "StoreKit 商品未列入服务端白名单");
    }

    private Long resolveOwner(JWSTransactionDecodedPayload payload) {
        if (payload.getAppAccountToken() != null) {
            List<Long> ids = jdbc.query("SELECT user_id FROM kod_ios_store_account WHERE app_account_token=?",
                    (rs, row) -> rs.getLong(1), payload.getAppAccountToken().toString());
            if (!ids.isEmpty()) return ids.get(0);
        }
        if (StringUtils.hasText(payload.getOriginalTransactionId())) {
            List<Long> ids = jdbc.query("SELECT user_id FROM kod_ios_store_transaction WHERE original_transaction_id=? ORDER BY created_at DESC LIMIT 1",
                    (rs, row) -> rs.getLong(1), payload.getOriginalTransactionId());
            if (!ids.isEmpty()) return ids.get(0);
        }
        return null;
    }

    private String accountToken(Long userId) {
        List<String> tokens = jdbc.query("SELECT app_account_token FROM kod_ios_store_account WHERE user_id=?",
                (rs, row) -> rs.getString(1), userId);
        if (!tokens.isEmpty()) return tokens.get(0);
        String token = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO kod_ios_store_account(user_id,app_account_token,created_at) VALUES(?,?,?)",
                    userId, token, System.currentTimeMillis());
            return token;
        } catch (DuplicateKeyException race) {
            return jdbc.queryForObject("SELECT app_account_token FROM kod_ios_store_account WHERE user_id=?", String.class, userId);
        }
    }

    private long creditBalance(Long userId) {
        Long result = jdbc.queryForObject("SELECT COALESCE(SUM(delta),0) FROM kod_ios_credit_ledger WHERE user_id=?", Long.class, userId);
        return result == null ? 0 : result;
    }

    private List<Map<String, Object>> activeSubscriptions(Long userId) {
        long now = System.currentTimeMillis();
        return jdbc.queryForList("""
                SELECT product_id,original_transaction_id,MAX(expires_at) AS expires_at
                FROM kod_ios_store_transaction
                WHERE user_id=? AND product_type='Auto-Renewable Subscription' AND revocation_at IS NULL AND expires_at>?
                GROUP BY product_id,original_transaction_id
                """, userId, now);
    }

    private Verified verifyTransaction(String signed) {
        VerificationException productionError = null;
        if (properties.getAppAppleId() != null) {
            try {
                return verifyTransactionForEnvironment(signed, Environment.PRODUCTION);
            } catch (BizException error) {
                if (error.getCause() instanceof VerificationException verification) productionError = verification;
                else throw error;
            }
        }
        try {
            return verifyTransactionForEnvironment(signed, Environment.SANDBOX);
        } catch (BizException sandboxError) {
            if (properties.isAllowXcodeTransactions()) {
                try {
                    return verifyTransactionForEnvironment(signed, Environment.XCODE);
                } catch (BizException ignored) {
                    // Preserve the real App Store verification failure below.
                }
            }
            throw new BizException(400, "Apple StoreKit 交易签名验证失败", productionError == null ? sandboxError : productionError);
        }
    }

    private Verified verifyTransactionForEnvironment(String signed, Environment environment) {
        try {
            return new Verified(verifier(environment).verifyAndDecodeTransaction(signed), environment);
        } catch (VerificationException error) {
            throw new BizException(400, "Apple StoreKit 交易签名验证失败", error);
        }
    }

    private VerifiedNotification verifyNotification(String signed) {
        if (properties.getAppAppleId() != null) {
            try {
                return new VerifiedNotification(verifier(Environment.PRODUCTION).verifyAndDecodeNotification(signed), Environment.PRODUCTION);
            } catch (VerificationException ignored) {
                // TestFlight and StoreKit sandbox notifications use the sandbox environment.
            }
        }
        try {
            return new VerifiedNotification(verifier(Environment.SANDBOX).verifyAndDecodeNotification(signed), Environment.SANDBOX);
        } catch (VerificationException error) {
            throw new BizException(400, "Apple Server Notification 签名验证失败", error);
        }
    }

    private SignedDataVerifier verifier(Environment environment) {
        if (properties.getRootCertificatePaths().isEmpty()) throw new BizException(503, "Apple 根证书尚未配置");
        if (environment == Environment.PRODUCTION && properties.getAppAppleId() == null) {
            throw new BizException(503, "生产环境 appAppleId 尚未配置");
        }
        Set<InputStream> roots = new HashSet<>();
        try {
            for (String path : properties.getRootCertificatePaths()) roots.add(new FileInputStream(path));
            return new SignedDataVerifier(roots, properties.getBundleId(),
                    environment == Environment.PRODUCTION ? properties.getAppAppleId() : null,
                    environment, environment != Environment.XCODE);
        } catch (IOException error) {
            throw new BizException(503, "无法读取 Apple 根证书", error);
        } finally {
            for (InputStream stream : roots) {
                try { stream.close(); } catch (IOException ignored) { }
            }
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new BizException(503, "StoreKit 权益服务尚未启用");
    }

    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) throw new BizException(400, "StoreKit 交易缺少 " + field);
        return value;
    }

    private record Verified(JWSTransactionDecodedPayload payload, Environment environment) { }
    private record VerifiedNotification(ResponseBodyV2DecodedPayload payload, Environment environment) { }
}
