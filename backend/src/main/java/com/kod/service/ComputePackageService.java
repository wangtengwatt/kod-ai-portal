package com.kod.service;

import com.kod.common.BizException;
import com.kod.config.ComputeCenterProperties;
import com.kod.util.ComputeDeliveryCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 固定 Token 套餐的购买、独立代理 Key 交付和输入/输出 Token 记账。 */
@Service
@RequiredArgsConstructor
public class ComputePackageService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ComputeCenterService center;
    private final ComputeDeliveryCrypto crypto;
    private final ComputeCenterProperties properties;

    /**
     * 为升级前已经购买、但当时尚未交付平台代理 Key 的套餐补齐交付。
     * 仅处理已经配置上游的算力套餐，且更新条件保证重复启动不会换掉用户现有 Key。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void provisionConfiguredPurchases() {
        List<Map<String, Object>> purchases = jdbc.queryForList("""
                SELECT a.id FROM compute_api_package_purchase a
                JOIN compute_product p ON p.id=a.product_id
                WHERE a.access_key_hash IS NULL
                  AND p.upstream_station_id IS NOT NULL AND p.upstream_key_id IS NOT NULL
                """);
        purchases.forEach(row -> provisionKey(number(row.get("id"))));
    }

    @Transactional
    public Map<String, Object> purchase(Long userId, Long productId, boolean autoTopUp) {
        Map<String, Object> product = one("""
                SELECT id, supplier_user_id AS supplierUserId, name, model_id AS modelId,
                       package_prompt_tokens AS promptTokens,
                       package_completion_tokens AS completionTokens,
                       package_price_card_hours AS priceCardHours, status, product_type AS productType,
                       upstream_station_id AS upstreamStationId, upstream_key_id AS upstreamKeyId
                FROM compute_product WHERE id=? FOR UPDATE
                """, productId);
        if (!"API".equals(product.get("productType")) || !"PUBLISHED".equals(product.get("status"))) {
            throw new BizException(400, "该模型 Token 套餐当前不可购买");
        }
        validateUpstream(product);
        long prompt = number(product.get("promptTokens"));
        long completion = number(product.get("completionTokens"));
        BigDecimal price = decimal(product.get("priceCardHours"));
        if (prompt <= 0 || completion <= 0 || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(409, "套餐配置不完整，请联系管理员");
        }
        center.ensureCardHoursForUserAction(userId, price, autoTopUp, null);
        String orderNo = "PKG" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 21).toUpperCase(Locale.ROOT);
        center.consumeAvailable(userId, price, false, "API_PACKAGE_PURCHASE", "PRODUCT", productId.toString(),
                "购买 " + product.get("name") + " Token 套餐", userId, "package:" + orderNo);
        jdbc.update("""
                INSERT INTO compute_order(order_no, user_id, order_type, product_id, card_hours, status, snapshot_json)
                VALUES (?, ?, 'API_PACKAGE', ?, ?, 'COMPLETED',
                    JSON_OBJECT('modelId', ?, 'promptTokens', ?, 'completionTokens', ?))
                """, orderNo, userId, productId, price, product.get("modelId"), prompt, completion);
        Long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        AccessKey accessKey = createAccessKey();
        jdbc.update("""
                INSERT INTO compute_api_package_purchase(
                    order_id, user_id, product_id, model_id, prompt_tokens_total, prompt_tokens_remaining,
                    completion_tokens_total, completion_tokens_remaining, price_card_hours, status,
                    access_key_hash, access_key_ciphertext, access_key_last4, key_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 'ACTIVE')
                """, orderId, userId, productId, product.get("modelId"), prompt, prompt,
                completion, completion, price, accessKey.hash(), accessKey.ciphertext(), accessKey.last4());
        Long purchaseId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Long supplierUserId = nullableLong(product.get("supplierUserId"));
        if (supplierUserId != null && !supplierUserId.equals(userId)) {
            BigDecimal feeRate = center.settingDecimal("platform_fee_rate", BigDecimal.ZERO);
            BigDecimal income = price.multiply(BigDecimal.ONE.subtract(feeRate)).setScale(3, RoundingMode.DOWN);
            if (income.compareTo(BigDecimal.ZERO) > 0) {
                center.credit(supplierUserId, income, "API_SALES_INCOME", orderNo, null,
                        "模型 Token 套餐销售收入", null, "package-income:" + orderNo);
            }
        }
        center.notifyUser(userId, "API_PACKAGE_PURCHASED", "Token 套餐购买成功",
                product.get("name") + "：输入 " + prompt + " / 输出 " + completion
                        + " Token；已交付到“我的资产 → Token 套餐”",
                "PACKAGE", purchaseId.toString());
        return Map.of("purchased", true, "orderNo", orderNo, "purchaseId", purchaseId,
                "productId", productId, "modelId", product.get("modelId"),
                "promptTokens", prompt, "completionTokens", completion, "priceCardHours", price);
    }

    /** 客户端内置对话/生图始终保留原零售站与人民币钱包流程。 */
    public Map<String, Object> authorize(Long userId, String rawModelId) {
        String modelId = Objects.toString(rawModelId, "").trim();
        return Map.of("managed", false, "allowed", true, "modelId", modelId,
                "message", "客户端内置调用保留原零售站与人民币钱包流程");
    }

    public List<Map<String, Object>> balances(Long userId) {
        return jdbc.queryForList("""
                SELECT model_id AS modelId,
                       SUM(prompt_tokens_total) AS promptTokensTotal,
                       SUM(prompt_tokens_remaining) AS promptTokensRemaining,
                       SUM(completion_tokens_total) AS completionTokensTotal,
                       SUM(completion_tokens_remaining) AS completionTokensRemaining,
                       SUM(price_card_hours) AS paidCardHours, MIN(create_time) AS firstPurchasedAt,
                       MAX(create_time) AS lastPurchasedAt
                FROM compute_api_package_purchase WHERE user_id=?
                GROUP BY model_id ORDER BY lastPurchasedAt DESC
                """, userId);
    }

    @Transactional
    public List<Map<String, Object>> purchases(Long userId) {
        List<Map<String, Object>> missingKeys = jdbc.queryForList("""
                SELECT a.id FROM compute_api_package_purchase a JOIN compute_product p ON p.id=a.product_id
                WHERE a.user_id=? AND a.access_key_hash IS NULL
                  AND p.upstream_station_id IS NOT NULL AND p.upstream_key_id IS NOT NULL
                FOR UPDATE
                """, userId);
        for (Map<String, Object> item : missingKeys) provisionKey(number(item.get("id")));

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.product_id AS productId, a.model_id AS modelId,
                       a.prompt_tokens_total AS promptTokensTotal,
                       a.prompt_tokens_remaining AS promptTokensRemaining,
                       a.completion_tokens_total AS completionTokensTotal,
                       a.completion_tokens_remaining AS completionTokensRemaining,
                       a.price_card_hours AS priceCardHours, a.status,
                       a.key_status AS keyStatus, a.access_key_last4 AS accessKeyLast4,
                       a.suspended_reason AS suspendedReason, a.create_time AS createTime,
                       p.name AS productName, o.order_no AS orderNo,
                       p.upstream_station_id AS upstreamStationId, p.upstream_key_id AS upstreamKeyId
                FROM compute_api_package_purchase a
                JOIN compute_product p ON p.id=a.product_id JOIN compute_order o ON o.id=a.order_id
                WHERE a.user_id=? ORDER BY a.id DESC LIMIT 300
                """, userId);
        rows.forEach(this::addDeliveryMetadata);
        return rows;
    }

    public Map<String, Object> credential(Long userId, Long purchaseId) {
        Map<String, Object> row = one("""
                SELECT id, user_id AS userId, model_id AS modelId, access_key_ciphertext AS ciphertext,
                       key_status AS keyStatus FROM compute_api_package_purchase WHERE id=?
                """, purchaseId);
        if (number(row.get("userId")) != userId) throw new BizException(403, "无权查看该套餐凭证");
        if (row.get("ciphertext") == null) throw new BizException(409, "该套餐尚未配置平台代理上游，请联系管理员");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("purchaseId", purchaseId);
        result.put("modelId", row.get("modelId"));
        result.put("baseUrl", properties.getProxyBaseUrl());
        result.put("apiKey", crypto.decrypt(row.get("ciphertext").toString()));
        result.put("apiFormat", "OpenAI Compatible");
        result.put("authenticationHeader", "Authorization: Bearer <API_KEY>");
        result.put("endpoints", List.of("/chat/completions", "/responses"));
        result.put("keyStatus", row.get("keyStatus"));
        return result;
    }

    @Transactional
    public Map<String, Object> regenerateKey(Long userId, Long purchaseId) {
        Map<String, Object> row = one("""
                SELECT id, user_id AS userId, prompt_tokens_remaining AS promptRemaining,
                       completion_tokens_remaining AS completionRemaining, in_flight AS inFlight
                FROM compute_api_package_purchase WHERE id=? FOR UPDATE
                """, purchaseId);
        if (number(row.get("userId")) != userId) throw new BizException(403, "无权重置该套餐 Key");
        if (number(row.get("inFlight")) != 0) throw new BizException(409, "该 Key 正在处理请求，请稍后重试");
        AccessKey accessKey = createAccessKey();
        boolean exhausted = number(row.get("promptRemaining")) <= 0 || number(row.get("completionRemaining")) <= 0;
        jdbc.update("""
                UPDATE compute_api_package_purchase SET access_key_hash=?, access_key_ciphertext=?,
                    access_key_last4=?, key_status=?, suspended_reason='', update_time=NOW() WHERE id=?
                """, accessKey.hash(), accessKey.ciphertext(), accessKey.last4(),
                exhausted ? "EXHAUSTED" : "ACTIVE", purchaseId);
        center.notifyUser(userId, "API_PACKAGE_KEY_REGENERATED", "套餐 API Key 已重新生成",
                "旧 Key 已立即失效，Token 剩余额度保持不变", "PACKAGE", purchaseId.toString());
        return credential(userId, purchaseId);
    }

    public List<Map<String, Object>> usage(Long userId) {
        return jdbc.queryForList("""
                SELECT id, request_id AS requestId, model_id AS modelId,
                       prompt_tokens AS promptTokens, completion_tokens AS completionTokens,
                       deducted_prompt_tokens AS deductedPromptTokens,
                       deducted_completion_tokens AS deductedCompletionTokens,
                       gifted_prompt_tokens AS giftedPromptTokens,
                       gifted_completion_tokens AS giftedCompletionTokens,
                       status, error_message AS errorMessage, create_time AS createTime
                FROM compute_api_proxy_request WHERE user_id=? ORDER BY id DESC LIMIT 300
                """, userId);
    }

    public List<Map<String, Object>> adminUpstreams(Long adminUserId) {
        center.requireAdmin(adminUserId);
        return jdbc.queryForList("""
                SELECT s.id AS stationId, s.url AS stationUrl, k.id AS keyId, k.status AS occupancyStatus,
                       CONCAT('••••', RIGHT(TRIM(k.api_key), 4)) AS keyLabel
                FROM relay_station s JOIN relay_station_key k ON k.station_id=s.id
                ORDER BY s.url, k.status, k.id
                """);
    }

    public List<Map<String, Object>> adminSuspendedKeys(Long adminUserId) {
        center.requireAdmin(adminUserId);
        return jdbc.queryForList("""
                SELECT a.id, a.user_id AS userId, u.email, a.product_id AS productId,
                       p.name AS productName, a.model_id AS modelId, a.key_status AS keyStatus,
                       a.access_key_last4 AS accessKeyLast4, a.suspended_reason AS suspendedReason,
                       a.update_time AS updateTime, s.url AS stationUrl
                FROM compute_api_package_purchase a JOIN sys_user u ON u.id=a.user_id
                JOIN compute_product p ON p.id=a.product_id
                LEFT JOIN relay_station s ON s.id=p.upstream_station_id
                WHERE a.key_status='SUSPENDED' ORDER BY a.update_time DESC
                """);
    }

    @Transactional
    public Map<String, Object> adminRepairKey(Long adminUserId, Long purchaseId, boolean regenerate) {
        center.requireAdmin(adminUserId);
        Map<String, Object> row = one("""
                SELECT id, user_id AS userId, prompt_tokens_remaining AS promptRemaining,
                       completion_tokens_remaining AS completionRemaining, in_flight AS inFlight
                FROM compute_api_package_purchase WHERE id=? FOR UPDATE
                """, purchaseId);
        if (number(row.get("inFlight")) != 0) throw new BizException(409, "该 Key 正在处理请求");
        boolean exhausted = number(row.get("promptRemaining")) <= 0 || number(row.get("completionRemaining")) <= 0;
        if (exhausted) throw new BizException(409, "该套餐输入或输出 Token 已耗尽，不能恢复");
        if (regenerate) {
            AccessKey key = createAccessKey();
            jdbc.update("""
                    UPDATE compute_api_package_purchase SET access_key_hash=?, access_key_ciphertext=?,
                        access_key_last4=?, key_status='ACTIVE', suspended_reason='', update_time=NOW() WHERE id=?
                    """, key.hash(), key.ciphertext(), key.last4(), purchaseId);
        } else {
            jdbc.update("UPDATE compute_api_package_purchase SET key_status='ACTIVE', suspended_reason='' WHERE id=?",
                    purchaseId);
        }
        center.notifyUser(number(row.get("userId")), "API_PACKAGE_KEY_RESTORED", "套餐 API Key 已恢复",
                regenerate ? "管理员已重新生成 Key，请到我的资产重新复制" : "管理员已恢复 Key，可以继续调用",
                "PACKAGE", purchaseId.toString());
        return Map.of("purchaseId", purchaseId, "keyStatus", "ACTIVE", "regenerated", regenerate);
    }

    @Transactional
    public ProxyAccess acquireProxy(String rawApiKey, String endpoint, String requestedModel) {
        String apiKey = Objects.toString(rawApiKey, "").trim();
        if (!apiKey.startsWith("kodpk_")) throw new BizException(401, "套餐 API Key 无效");
        String hash = crypto.fingerprint(apiKey);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id AS purchaseId, a.user_id AS userId, a.product_id AS productId,
                       a.model_id AS modelId, a.prompt_tokens_remaining AS promptRemaining,
                       a.completion_tokens_remaining AS completionRemaining,
                       a.key_status AS keyStatus, a.in_flight AS inFlight,
                       p.upstream_station_id AS stationId, p.upstream_key_id AS upstreamKeyId,
                       s.url AS stationUrl, k.api_key AS upstreamApiKey
                FROM compute_api_package_purchase a JOIN compute_product p ON p.id=a.product_id
                LEFT JOIN relay_station s ON s.id=p.upstream_station_id
                LEFT JOIN relay_station_key k ON k.id=p.upstream_key_id AND k.station_id=p.upstream_station_id
                WHERE a.access_key_hash=? FOR UPDATE
                """, hash);
        if (rows.isEmpty()) throw new BizException(401, "套餐 API Key 无效");
        Map<String, Object> row = rows.get(0);
        String status = Objects.toString(row.get("keyStatus"), "");
        if ("SUSPENDED".equals(status)) throw new BizException(423, "套餐 Key 已暂停，请联系管理员");
        if (!"ACTIVE".equals(status)) throw new BizException(402, "套餐输入或输出 Token 额度已耗尽");
        if (number(row.get("promptRemaining")) <= 0 || number(row.get("completionRemaining")) <= 0) {
            jdbc.update("UPDATE compute_api_package_purchase SET key_status='EXHAUSTED', in_flight=0 WHERE id=?",
                    row.get("purchaseId"));
            throw new BizException(402, "套餐输入或输出 Token 额度已耗尽");
        }
        String fixedModel = Objects.toString(row.get("modelId"), "");
        if (!fixedModel.equals(requestedModel)) {
            throw new BizException(400, "该套餐 Key 只能调用模型：" + fixedModel);
        }
        if (row.get("stationUrl") == null || row.get("upstreamApiKey") == null) {
            throw new BizException(503, "套餐上游零售站配置不可用");
        }
        int claimed = jdbc.update("""
                UPDATE compute_api_package_purchase SET in_flight=1
                WHERE id=? AND in_flight=0 AND key_status='ACTIVE'
                """, row.get("purchaseId"));
        if (claimed == 0) throw new BizException(409, "该套餐 Key 正在处理另一请求，请稍后重试");
        String requestId = "pxy_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO compute_api_proxy_request(
                    request_id, package_purchase_id, user_id, product_id, endpoint, model_id, status)
                VALUES (?, ?, ?, ?, ?, ?, 'IN_FLIGHT')
                """, requestId, row.get("purchaseId"), row.get("userId"), row.get("productId"), endpoint, fixedModel);
        return new ProxyAccess(requestId, number(row.get("purchaseId")), number(row.get("userId")),
                number(row.get("productId")), fixedModel, row.get("stationUrl").toString(),
                row.get("upstreamApiKey").toString().trim());
    }

    @Transactional
    public void completeProxy(ProxyAccess access, long promptTokens, long completionTokens, int upstreamStatus) {
        Map<String, Object> row = one("""
                SELECT prompt_tokens_remaining AS promptRemaining,
                       completion_tokens_remaining AS completionRemaining
                FROM compute_api_package_purchase WHERE id=? FOR UPDATE
                """, access.purchaseId());
        long safePrompt = Math.max(0, promptTokens);
        long safeCompletion = Math.max(0, completionTokens);
        long promptTake = Math.min(safePrompt, number(row.get("promptRemaining")));
        long completionTake = Math.min(safeCompletion, number(row.get("completionRemaining")));
        long promptAfter = number(row.get("promptRemaining")) - promptTake;
        long completionAfter = number(row.get("completionRemaining")) - completionTake;
        boolean exhausted = promptAfter <= 0 || completionAfter <= 0;
        jdbc.update("""
                UPDATE compute_api_package_purchase
                SET prompt_tokens_remaining=?, completion_tokens_remaining=?,
                    status=?, key_status=?, in_flight=0, update_time=NOW() WHERE id=?
                """, promptAfter, completionAfter, exhausted ? "EXHAUSTED" : "ACTIVE",
                exhausted ? "EXHAUSTED" : "ACTIVE", access.purchaseId());
        jdbc.update("""
                UPDATE compute_api_proxy_request SET prompt_tokens=?, completion_tokens=?,
                    deducted_prompt_tokens=?, deducted_completion_tokens=?,
                    gifted_prompt_tokens=?, gifted_completion_tokens=?, upstream_status=?,
                    status=?, completed_at=NOW() WHERE request_id=?
                """, safePrompt, safeCompletion, promptTake, completionTake,
                safePrompt - promptTake, safeCompletion - completionTake, upstreamStatus,
                (safePrompt > promptTake || safeCompletion > completionTake) ? "GIFTED_OVERAGE" : "PAID",
                access.requestId());
        if (exhausted) {
            center.notifyUser(access.userId(), "API_PACKAGE_EXHAUSTED", "Token 套餐额度已耗尽",
                    "本次请求已完整返回；下一次请求将被拒绝，请购买新套餐", "API_REQUEST", access.requestId());
        }
    }

    @Transactional
    public void failProxy(ProxyAccess access, int upstreamStatus, String reason, boolean suspend) {
        String safeReason = safe(reason, 512);
        jdbc.update("""
                UPDATE compute_api_package_purchase SET in_flight=0,
                    key_status=CASE WHEN ? THEN 'SUSPENDED' ELSE key_status END,
                    suspended_reason=CASE WHEN ? THEN ? ELSE suspended_reason END,
                    update_time=NOW() WHERE id=?
                """, suspend, suspend, safeReason, access.purchaseId());
        jdbc.update("""
                UPDATE compute_api_proxy_request SET upstream_status=?, status=?, error_message=?, completed_at=NOW()
                WHERE request_id=?
                """, upstreamStatus, suspend ? "USAGE_MISSING" : "UPSTREAM_ERROR", safeReason, access.requestId());
        if (suspend) {
            center.notifyUser(access.userId(), "API_PACKAGE_KEY_SUSPENDED", "套餐 API Key 已暂停",
                    "上游未返回可核验 usage，本次结果已完整交付；为避免免费调用，下一次请求已暂停",
                    "PACKAGE", Long.toString(access.purchaseId()));
        }
    }

    private void provisionKey(long purchaseId) {
        AccessKey accessKey = createAccessKey();
        jdbc.update("""
                UPDATE compute_api_package_purchase SET access_key_hash=?, access_key_ciphertext=?,
                    access_key_last4=?, key_status=CASE
                      WHEN prompt_tokens_remaining<=0 OR completion_tokens_remaining<=0 THEN 'EXHAUSTED'
                      ELSE 'ACTIVE' END
                WHERE id=? AND access_key_hash IS NULL
                """, accessKey.hash(), accessKey.ciphertext(), accessKey.last4(), purchaseId);
    }

    private void addDeliveryMetadata(Map<String, Object> row) {
        row.put("baseUrl", properties.getProxyBaseUrl());
        row.put("apiFormat", "OpenAI Compatible");
        row.put("authenticationHeader", "Authorization: Bearer <API_KEY>");
        row.put("endpoints", List.of("/chat/completions", "/responses"));
    }

    private void validateUpstream(Map<String, Object> product) {
        Long stationId = nullableLong(product.get("upstreamStationId"));
        Long keyId = nullableLong(product.get("upstreamKeyId"));
        if (stationId == null || keyId == null) {
            throw new BizException(409, "该套餐尚未配置上游零售站，暂不可购买");
        }
        Long matches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relay_station_key WHERE id=? AND station_id=?", Long.class, keyId, stationId);
        if (matches == null || matches == 0) throw new BizException(409, "套餐上游 API Key 配置无效");
    }

    private AccessKey createAccessKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String plaintext = "kodpk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new AccessKey(crypto.fingerprint(plaintext), crypto.encrypt(plaintext),
                plaintext.substring(plaintext.length() - 4));
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new BizException(404, "记录不存在");
        return rows.get(0);
    }

    private static long number(Object value) {
        return value == null ? 0 : value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : number(value);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO.setScale(3);
        return (value instanceof BigDecimal b ? b : new BigDecimal(value.toString())).setScale(3, RoundingMode.HALF_UP);
    }

    private static String safe(String value, int maxLength) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private record AccessKey(String hash, String ciphertext, String last4) {
    }

    public record ProxyAccess(String requestId, long purchaseId, long userId, long productId,
                              String modelId, String stationUrl, String upstreamApiKey) {
    }
}
