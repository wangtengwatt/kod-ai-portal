package com.kod.service;

import com.kod.common.BizException;
import com.kod.common.CardHourBalanceException;
import com.kod.config.ComputeCenterProperties;
import com.kod.entity.ApiRequestLog;
import com.kod.util.ComputeDeliveryCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * KOD 算力中心核心领域服务。
 *
 * <p>该服务刻意使用独立的 {@code compute_*} 表，不修改同事维护的用户、充值、订单和用量表结构。
 * 仅在用户主动购买卡时时，以事务方式扣减存量 {@code sys_user.balance}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeCenterService {

    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final BigDecimal MIN_PURCHASE = new BigDecimal("0.1");
    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("0.1");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final String PURPOSE_RESERVATION = "GPU_RESERVATION";
    private static final String PURPOSE_TRANSFER = "TRANSFER";
    private static final String MARKETPLACE_FIXED = "MARKETPLACE_FIXED";
    private static final long MAX_PRODUCT_IMAGE_BYTES = 8L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final ComputeCenterProperties properties;
    private final ComputeDeliveryCrypto deliveryCrypto;
    private final ComputeTrustService trustService;
    private final ComputePrivateDocumentStorage documentStorage;
    private final ComputeReferralService referralService;

    // ---------------------------------------------------------------------
    // Public market and account
    // ---------------------------------------------------------------------

    public Map<String, Object> publicConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("cardHourCnyRate", settingDecimal("card_hour_cny_rate", properties.getDefaultCardHourCnyRate()));
        result.put("cardHourRedeemRate", settingDecimal("card_hour_redeem_rate", BigDecimal.ONE));
        result.put("usdCnyRate", settingDecimal("usd_cny_rate", properties.getDefaultUsdCnyRate()));
        result.put("unitName", "KAI 标准卡时");
        result.put("currency", "CNY");
        return result;
    }

    public List<Map<String, Object>> listProducts(String type, boolean includeUnpublished, Long userId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.supplier_user_id AS supplierUserId, p.node_id AS nodeId,
                       p.product_type AS productType,
                       p.name, p.description, p.region, p.status, p.model_id AS modelId,
                       p.prompt_rate_per_million AS promptRatePerMillion,
                       p.completion_rate_per_million AS completionRatePerMillion,
                       p.package_prompt_tokens AS packagePromptTokens,
                       p.package_completion_tokens AS packageCompletionTokens,
                       p.package_price_card_hours AS packagePriceCardHours,
                       p.upstream_station_id AS upstreamStationId, p.upstream_key_id AS upstreamKeyId,
                       p.gpu_model AS gpuModel, p.gpu_memory_gb AS gpuMemoryGb,
                       p.gpu_count AS gpuCount, p.price_per_gpu_hour AS pricePerGpuHour,
                       p.trade_mode AS tradeMode, p.package_duration_hours AS packageDurationHours,
                       p.delivery_deadline_hours AS deliveryDeadlineHours,
                       p.available_from AS availableFrom, p.available_to AS availableTo,
                       p.delivery_mode AS deliveryMode, p.sla_description AS slaDescription,
                       p.is_test AS isTest, p.rejection_reason AS rejectionReason,
                       p.create_time AS createTime,
                       (SELECT i.id FROM compute_product_image i WHERE i.product_id=p.id ORDER BY i.sort_order,i.id LIMIT 1) AS coverImageId,
                       s.display_name AS supplierName
                FROM compute_product p
                LEFT JOIN compute_supplier s ON s.user_id = p.supplier_user_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (!includeUnpublished) {
            sql.append(" AND p.status = 'PUBLISHED'");
            sql.append(" AND (p.product_type <> 'GPU' OR p.trade_mode = 'MARKETPLACE_FIXED')");
            sql.append(" AND (p.product_type <> 'GPU' OR EXISTS (SELECT 1 FROM compute_gpu_node n WHERE n.id=p.node_id AND n.status='RUNNING'))");
        } else if (userId != null) {
            sql.append(" AND p.supplier_user_id = ?");
            args.add(userId);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND p.product_type = ?");
            args.add(type.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY p.published_at DESC, p.id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> getProduct(Long productId) {
        try {
            return jdbc.queryForMap("""
                    SELECT p.id, p.supplier_user_id AS supplierUserId, p.node_id AS nodeId,
                           p.product_type AS productType,
                           p.name, p.description, p.region, p.status, p.model_id AS modelId,
                           p.prompt_rate_per_million AS promptRatePerMillion,
                           p.completion_rate_per_million AS completionRatePerMillion,
                           p.package_prompt_tokens AS packagePromptTokens,
                           p.package_completion_tokens AS packageCompletionTokens,
                           p.package_price_card_hours AS packagePriceCardHours,
                           p.upstream_station_id AS upstreamStationId, p.upstream_key_id AS upstreamKeyId,
                           p.gpu_model AS gpuModel, p.gpu_memory_gb AS gpuMemoryGb,
                           p.gpu_count AS gpuCount, p.price_per_gpu_hour AS pricePerGpuHour,
                           p.trade_mode AS tradeMode, p.package_duration_hours AS packageDurationHours,
                           p.delivery_deadline_hours AS deliveryDeadlineHours,
                           p.available_from AS availableFrom, p.available_to AS availableTo,
                           p.delivery_mode AS deliveryMode, p.sla_description AS slaDescription,
                           p.is_test AS isTest, p.rejection_reason AS rejectionReason,
                           p.create_time AS createTime,
                           (SELECT i.id FROM compute_product_image i WHERE i.product_id=p.id ORDER BY i.sort_order,i.id LIMIT 1) AS coverImageId,
                           s.display_name AS supplierName
                    FROM compute_product p
                    LEFT JOIN compute_supplier s ON s.user_id = p.supplier_user_id
                    WHERE p.id = ?
                    """, productId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(404, "算力商品不存在");
        }
    }

    @Transactional
    public Map<String, Object> getAccount(Long userId) {
        ensureAccount(userId);
        Map<String, Object> account = jdbc.queryForMap("""
                SELECT u.id AS userId, u.email, u.balance AS cnyBalance,
                       a.available_card_hours AS availableCardHours,
                       a.frozen_card_hours AS frozenCardHours,
                       a.lifetime_income AS lifetimeIncome,
                       a.lifetime_consumption AS lifetimeConsumption,
                       COALESCE(s.status, 'NONE') AS supplierStatus
                FROM sys_user u
                JOIN compute_account a ON a.user_id = u.id
                LEFT JOIN compute_supplier s ON s.user_id = u.id
                WHERE u.id = ?
                """, userId);
        account.put("isAdmin", properties.isAdminEmail(Objects.toString(account.get("email"), "")));
        String supplierStatus = Objects.toString(account.get("supplierStatus"), "NONE");
        List<String> roles = new ArrayList<>();
        roles.add("BUYER");
        if ("APPROVED".equals(supplierStatus)) roles.add("SUPPLIER");
        if (Boolean.TRUE.equals(account.get("isAdmin"))) roles.add("ADMIN");
        account.put("roles", roles);
        account.put("withdrawableCardHours", account.get("availableCardHours"));
        BigDecimal rentalIncome = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM compute_ledger
                WHERE user_id=? AND direction='CREDIT'
                  AND (entry_type='GPU_RENTAL_INCOME'
                    OR (entry_type='SUPPLIER_INCOME' AND reference_id REGEXP '^[0-9]+$'))
                """, BigDecimal.class, userId);
        account.put("rentalIncome", rentalIncome);
        account.put("apiSalesIncome", jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM compute_ledger
                WHERE user_id=? AND direction='CREDIT'
                  AND (entry_type='API_SALES_INCOME'
                    OR (entry_type='SUPPLIER_INCOME' AND reference_id NOT REGEXP '^[0-9]+$'))
                """, BigDecimal.class, userId));
        account.put("identityStatus", jdbc.queryForObject("""
                SELECT COALESCE((SELECT status FROM compute_identity_verification
                  WHERE user_id=? ORDER BY id DESC LIMIT 1),'NONE')
                """, String.class, userId));
        Map<String, Object> deviceCounts = new HashMap<>();
        for (String status : List.of("PENDING", "DEPLOYING", "RUNNING", "PENDING_ACTION")) {
            deviceCounts.put(status, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM compute_gpu_node WHERE supplier_user_id=? AND status=?",
                    Long.class, userId, status));
        }
        account.put("deviceCounts", deviceCounts);
        Map<String, Object> gpuAssetCounts = new HashMap<>();
        for (String status : List.of("PENDING", "REJECTED", "RUNNING", "OFFLINE")) {
            gpuAssetCounts.put(status, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM compute_gpu_node WHERE supplier_user_id=? AND status=?",
                    Long.class, userId, status));
        }
        gpuAssetCounts.put("PENDING_DELIVERY", jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_reservation
                WHERE supplier_user_id=? AND trade_mode='MARKETPLACE_FIXED' AND status='PENDING_DELIVERY'
                """, Long.class, userId));
        gpuAssetCounts.put("ACTIVE_RENTAL", jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_reservation
                WHERE supplier_user_id=? AND trade_mode='MARKETPLACE_FIXED'
                  AND start_time<=NOW() AND end_time>NOW() AND status IN ('DELIVERED','COMPLETED')
                """, Long.class, userId));
        Long nodePendingAction = jdbc.queryForObject(
                "SELECT COUNT(*) FROM compute_gpu_node WHERE supplier_user_id=? AND status='PENDING_ACTION'",
                Long.class, userId);
        Long orderPendingAction = jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_reservation
                WHERE supplier_user_id=? AND trade_mode='MARKETPLACE_FIXED'
                  AND status IN ('DISPUTED','EXCEPTION_PENDING')
                """, Long.class, userId);
        gpuAssetCounts.put("PENDING_ACTION", (nodePendingAction == null ? 0 : nodePendingAction)
                + (orderPendingAction == null ? 0 : orderPendingAction));
        account.put("gpuAssetCounts", gpuAssetCounts);

        Map<String, Object> referralSummary = referralService.summary(userId);
        BigDecimal commissionIncome = decimal(referralSummary.get("commissionIncome"), 4);
        BigDecimal redeemRate = settingDecimal("card_hour_redeem_rate", BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal rentalIncomeCny = decimal(rentalIncome, 3).multiply(redeemRate)
                .setScale(4, RoundingMode.HALF_UP);
        account.putAll(referralSummary);
        account.put("rentalIncomeCnyEquivalent", rentalIncomeCny);
        account.put("totalIncomeCny", rentalIncomeCny.add(commissionIncome).setScale(4, RoundingMode.HALF_UP));
        account.putAll(publicConfig());
        account.put("unreadNotifications", jdbc.queryForObject(
                "SELECT COUNT(*) FROM compute_notification WHERE user_id = ? AND is_read = 0", Long.class, userId));
        return account;
    }

    @Transactional
    public Map<String, Object> purchaseCardHours(Long userId, BigDecimal requestedAmount) {
        BigDecimal amount = normalizePurchaseAmount(requestedAmount);
        BigDecimal rate = settingDecimal("card_hour_cny_rate", properties.getDefaultCardHourCnyRate());
        BigDecimal cost = cardHourPurchaseCost(amount, rate);

        Map<String, Object> user = lockUser(userId);
        BigDecimal balance = decimal(user.get("balance"), 4);
        if (balance.compareTo(cost) < 0) {
            throw new BizException(400, "人民币余额不足，请先充值");
        }
        completeCardHourPurchase(userId, amount, cost, rate, false);
        return getAccount(userId);
    }

    /**
     * 为用户主动发起的消费原子补足卡时。未获得用户确认时只返回结构化报价，不做扣款。
     */
    void ensureCardHoursForUserAction(Long userId, BigDecimal rawRequired, boolean autoTopUp,
                                      LocalDateTime validUntil) {
        BigDecimal required = scale3(rawRequired);
        if (required.compareTo(ZERO) <= 0) throw new BizException(400, "消费卡时必须大于 0");

        Map<String, Object> user = lockUser(userId);
        Map<String, Object> account = lockAccount(userId);
        BigDecimal available = validUntil == null
                ? decimal(account.get("availableCardHours"), 3)
                : decimal(jdbc.queryForObject("""
                        SELECT COALESCE(SUM(remaining_amount-frozen_amount), 0)
                        FROM compute_card_hour_lot
                        WHERE owner_user_id=? AND remaining_amount>frozen_amount
                          AND asset_type='STANDARD' AND custody_status='ACTIVE'
                          AND (expires_at IS NULL OR expires_at>?)
                        """, BigDecimal.class, userId, timestamp(validUntil)), 3);
        if (available.compareTo(required) >= 0) return;

        BigDecimal rate = settingDecimal("card_hour_cny_rate", properties.getDefaultCardHourCnyRate());
        BigDecimal cnyBalance = decimal(user.get("balance"), 4);
        Map<String, Object> quote = buildCardHourTopUpQuote(required, available, rate, cnyBalance);
        BigDecimal purchaseAmount = decimal(quote.get("purchaseCardHours"), 3);
        BigDecimal cost = decimal(quote.get("cnyCost"), 4);
        BigDecimal cnyShortfall = decimal(quote.get("cnyShortfall"), 4);
        boolean canAutoTopUp = Boolean.TRUE.equals(quote.get("canAutoTopUp"));

        if (!autoTopUp || !canAutoTopUp) {
            int code = canAutoTopUp ? CardHourBalanceException.TOP_UP_CONFIRM_REQUIRED
                    : CardHourBalanceException.CNY_BALANCE_INSUFFICIENT;
            String message = canAutoTopUp
                    ? "可用卡时不足，可使用人民币余额自动补足后继续"
                    : "卡时与人民币余额均不足，还差 ¥" + cnyShortfall.toPlainString();
            throw new CardHourBalanceException(code, message, quote);
        }

        completeCardHourPurchase(userId, purchaseAmount, cost, rate, true);
    }

    static Map<String, Object> buildCardHourTopUpQuote(BigDecimal rawRequired, BigDecimal rawAvailable,
                                                       BigDecimal rawRate, BigDecimal rawCnyBalance) {
        BigDecimal required = decimal(rawRequired, 3);
        BigDecimal available = decimal(rawAvailable, 3).max(ZERO);
        BigDecimal rate = decimal(rawRate, 4);
        BigDecimal cnyBalance = decimal(rawCnyBalance, 4).max(BigDecimal.ZERO);
        BigDecimal shortage = required.subtract(available).max(ZERO).setScale(3, RoundingMode.HALF_UP);
        BigDecimal purchaseAmount = shortage.divide(MIN_PURCHASE, 0, RoundingMode.CEILING)
                .multiply(MIN_PURCHASE).setScale(3, RoundingMode.UNNECESSARY);
        BigDecimal cost = purchaseAmount.multiply(rate).setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal cnyShortfall = cost.subtract(cnyBalance).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);

        Map<String, Object> quote = new HashMap<>();
        quote.put("requiredCardHours", required);
        quote.put("availableCardHours", available);
        quote.put("shortageCardHours", shortage);
        quote.put("purchaseCardHours", purchaseAmount);
        quote.put("cardHourCnyRate", rate);
        quote.put("cnyCost", cost);
        quote.put("cnyBalance", cnyBalance);
        quote.put("cnyShortfall", cnyShortfall);
        quote.put("canAutoTopUp", cnyShortfall.compareTo(BigDecimal.ZERO) == 0);
        return quote;
    }

    private BigDecimal cardHourPurchaseCost(BigDecimal amount, BigDecimal rate) {
        try {
            return amount.multiply(rate).setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BizException(400, "购买数量与人民币钱包精度不兼容，请按 0.1 卡时的倍数购买");
        }
    }

    private void completeCardHourPurchase(Long userId, BigDecimal amount, BigDecimal cost,
                                          BigDecimal rate, boolean autoTopUp) {
        jdbc.update("UPDATE sys_user SET balance = balance - ? WHERE id = ?", cost, userId);

        String orderNo = "CH" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO compute_order(order_no, user_id, order_type, card_hours, cny_amount, status, snapshot_json)
                VALUES (?, ?, 'CARD_HOUR_PURCHASE', ?, ?, 'COMPLETED', JSON_OBJECT('rate', ?, 'autoTopUp', ?))
                """, orderNo, userId, amount, cost, rate.toPlainString(), autoTopUp);
        credit(userId, amount, "PURCHASE", orderNo, null, "人民币余额购买卡时", userId,
                "purchase:" + orderNo);
        notifyUser(userId, "CARD_HOUR_PURCHASED", "卡时购买成功",
                (autoTopUp ? "已自动补足 " : "已购买 ") + amount.toPlainString()
                        + " 卡时，支付 ¥" + cost.toPlainString(), "ORDER", orderNo);
    }

    public List<Map<String, Object>> listLedger(Long userId) {
        return jdbc.queryForList("""
                SELECT id, entry_type AS entryType, direction, amount,
                       available_after AS availableAfter, frozen_after AS frozenAfter,
                       reference_type AS referenceType, reference_id AS referenceId,
                       description, create_time AS createTime
                FROM compute_ledger WHERE user_id = ? ORDER BY id DESC LIMIT 300
                """, userId);
    }

    public List<Map<String, Object>> listOrders(Long userId) {
        return jdbc.queryForList("""
                SELECT o.id, o.order_no AS orderNo, o.order_type AS orderType, o.product_id AS productId,
                       o.card_hours AS cardHours, o.cny_amount AS cnyAmount, o.status,
                       o.create_time AS createTime, p.name AS productName,
                       COALESCE(
                           CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(o.snapshot_json, '$.coverImageId')), 'null') AS UNSIGNED),
                           (SELECT i.id FROM compute_product_image i
                            WHERE i.product_id=o.product_id ORDER BY i.sort_order,i.id LIMIT 1)
                       ) AS coverImageId
                FROM compute_order o LEFT JOIN compute_product p ON p.id = o.product_id
                WHERE o.user_id = ? ORDER BY o.id DESC LIMIT 200
                """, userId);
    }

    @Transactional
    public Map<String, Object> withdrawToCnyWallet(Long userId, BigDecimal rawAmount, String rawRequestId) {
        BigDecimal amount = normalizeWithdrawalAmount(rawAmount);
        String requestId = safe(Objects.toString(rawRequestId, ""), 96);
        if (requestId.isBlank()) requestId = UUID.randomUUID().toString();
        Map<String, Object> user = lockUser(userId);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, withdrawal_no AS withdrawalNo, request_id AS requestId,
                       card_hours AS cardHours, redeem_rate AS redeemRate, cny_amount AS cnyAmount,
                       status, destination_type AS destinationType,
                       card_hours_before AS cardHoursBefore, card_hours_after AS cardHoursAfter,
                       cny_balance_before AS cnyBalanceBefore, cny_balance_after AS cnyBalanceAfter,
                       completed_at AS completedAt, create_time AS createTime
                FROM compute_withdrawal WHERE user_id=? AND request_id=?
                """, userId, requestId);
        if (!existing.isEmpty()) return existing.get(0);

        Map<String, Object> account = lockAccount(userId);
        BigDecimal availableBefore = decimal(account.get("availableCardHours"), 3);
        if (availableBefore.compareTo(amount) < 0) {
            throw new BizException(400, "可用卡时不足；冻结中的订单卡时不能兑换");
        }
        BigDecimal rate = settingDecimal("card_hour_redeem_rate", BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
        BigDecimal cnyAmount = amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal cnyBefore = decimal(user.get("balance"), 4);
        String withdrawalNo = "WD" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 22).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO compute_withdrawal(
                    withdrawal_no, request_id, user_id, card_hours, redeem_rate, cny_amount, status,
                    card_hours_before, cny_balance_before)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, withdrawalNo, requestId, userId, amount, rate, cnyAmount, availableBefore, cnyBefore);
        debitAvailableWithoutConsumption(userId, amount, "WITHDRAWAL", "WITHDRAWAL", withdrawalNo,
                "卡时兑换到 KOD 内部人民币钱包", userId, "withdrawal:" + withdrawalNo);
        jdbc.update("UPDATE sys_user SET balance=balance+? WHERE id=?", cnyAmount, userId);
        BigDecimal availableAfter = availableBefore.subtract(amount).setScale(3, RoundingMode.HALF_UP);
        BigDecimal cnyAfter = cnyBefore.add(cnyAmount).setScale(4, RoundingMode.HALF_UP);
        jdbc.update("""
                UPDATE compute_withdrawal SET status='COMPLETED', card_hours_after=?, cny_balance_after=?,
                    completed_at=NOW() WHERE withdrawal_no=?
                """, availableAfter, cnyAfter, withdrawalNo);
        notifyUser(userId, "WITHDRAWAL_COMPLETED", "卡时已兑换到人民币钱包",
                amount + " 卡时已兑换为 ¥" + cnyAmount + "；仅限 KOD 内部人民币钱包", "WITHDRAWAL", withdrawalNo);
        return listWithdrawals(userId).stream().filter(item -> withdrawalNo.equals(item.get("withdrawalNo")))
                .findFirst().orElseThrow();
    }

    public List<Map<String, Object>> listWithdrawals(Long userId) {
        return jdbc.queryForList("""
                SELECT id, withdrawal_no AS withdrawalNo, request_id AS requestId,
                       card_hours AS cardHours, redeem_rate AS redeemRate, cny_amount AS cnyAmount,
                       status, destination_type AS destinationType,
                       card_hours_before AS cardHoursBefore, card_hours_after AS cardHoursAfter,
                       cny_balance_before AS cnyBalanceBefore, cny_balance_after AS cnyBalanceAfter,
                       completed_at AS completedAt, create_time AS createTime
                FROM compute_withdrawal WHERE user_id=? ORDER BY id DESC LIMIT 300
                """, userId);
    }

    // ---------------------------------------------------------------------
    // API product activation and billing
    // ---------------------------------------------------------------------

    @Transactional
    public Map<String, Object> activateApiProduct(Long userId, Long productId) {
        Map<String, Object> product = getProduct(productId);
        if (!"API".equals(product.get("productType")) || !"PUBLISHED".equals(product.get("status"))) {
            throw new BizException(400, "该模型 API 商品当前不可开通");
        }
        ensureAccount(userId);
        BigDecimal available = jdbc.queryForObject(
                "SELECT available_card_hours FROM compute_account WHERE user_id = ?", BigDecimal.class, userId);
        if (available == null || available.compareTo(ZERO) <= 0) {
            throw new BizException(400, "请先购买或领取卡时");
        }
        jdbc.update("""
                INSERT INTO compute_product_activation(user_id, product_id, status)
                VALUES (?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE status='ACTIVE', update_time=CURRENT_TIMESTAMP
                """, userId, productId);
        String orderNo = "API" + UUID.randomUUID().toString().replace("-", "").substring(0, 22).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO compute_order(order_no, user_id, order_type, product_id, status, snapshot_json)
                VALUES (?, ?, 'API_ACTIVATION', ?, 'COMPLETED', JSON_OBJECT('modelId', ?))
                """, orderNo, userId, productId, Objects.toString(product.get("modelId"), ""));
        notifyUser(userId, "API_ACTIVATED", "模型 API 已开通",
                product.get("name") + " 已加入可用商品，实际调用按 Token 扣除卡时", "PRODUCT", productId.toString());
        return Map.of("activated", true, "productId", productId, "orderNo", orderNo);
    }

    public List<Map<String, Object>> listActivations(Long userId) {
        return jdbc.queryForList("""
                SELECT a.id, a.product_id AS productId, a.status, a.create_time AS createTime,
                       p.name AS productName, p.model_id AS modelId,
                       p.prompt_rate_per_million AS promptRatePerMillion,
                       p.completion_rate_per_million AS completionRatePerMillion
                FROM compute_product_activation a
                JOIN compute_product p ON p.id = a.product_id
                WHERE a.user_id = ? ORDER BY a.id DESC
                """, userId);
    }

    public List<Map<String, Object>> listApiUsage(Long userId) {
        return jdbc.queryForList("""
                SELECT id, request_id AS requestId, model_id AS modelId,
                       prompt_tokens AS promptTokens, completion_tokens AS completionTokens,
                       requested_card_hours AS requestedCardHours,
                       charged_card_hours AS chargedCardHours,
                       unpaid_card_hours AS unpaidCardHours, status, create_time AS createTime
                FROM compute_api_usage_charge
                WHERE user_id=? ORDER BY id DESC LIMIT 300
                """, userId);
    }

    /**
     * 将 new-api 的可信用量日志路由到卡时账本。
     *
     * @return true 表示该调用属于已开通的算力中心商品，旧人民币扣费必须跳过；false 继续旧逻辑。
     */
    @Transactional
    public boolean billApiUsage(ApiRequestLog logEntry) {
        if (logEntry == null || logEntry.getUserId() == null || logEntry.getId() == null) return false;
        if (!isSchemaAvailable()) return false;

        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT p.id, p.supplier_user_id AS supplierUserId,
                       p.prompt_rate_per_million AS promptRate,
                       p.completion_rate_per_million AS completionRate
                FROM compute_product_activation a
                JOIN compute_product p ON p.id = a.product_id
                WHERE a.user_id = ? AND a.status = 'ACTIVE' AND p.status = 'PUBLISHED'
                  AND p.product_type = 'API' AND p.model_id = ?
                ORDER BY p.id LIMIT 1
                """, logEntry.getUserId(), Objects.toString(logEntry.getModelName(), ""));
        if (matches.isEmpty()) return false;

        Map<String, Object> product = matches.get(0);
        BigDecimal promptRate = decimal(product.get("promptRate"), 6);
        BigDecimal completionRate = decimal(product.get("completionRate"), 6);
        int promptTokens = Math.max(0, intValue(logEntry.getPromptTokens()));
        int completionTokens = Math.max(0, intValue(logEntry.getCompletionTokens()));
        BigDecimal requested = promptRate.multiply(BigDecimal.valueOf(promptTokens))
                .add(completionRate.multiply(BigDecimal.valueOf(completionTokens)))
                .divide(ONE_MILLION, 3, RoundingMode.CEILING);
        if (requested.compareTo(ZERO) <= 0) requested = new BigDecimal("0.001");

        String requestId = Objects.toString(logEntry.getRequestId(), "").trim();
        if (requestId.isEmpty()) requestId = "log:" + logEntry.getId();

        // 先用两个唯一键占住本条用量。并发重试会在这里等待并返回 0，不会重复扣卡时。
        int claimed = jdbc.update("""
                INSERT IGNORE INTO compute_api_usage_charge(
                    api_request_log_id, request_id, user_id, product_id, model_id,
                    prompt_tokens, completion_tokens, requested_card_hours,
                    charged_card_hours, unpaid_card_hours, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0.000, ?, 'PENDING')
                """, logEntry.getId(), requestId, logEntry.getUserId(), product.get("id"),
                Objects.toString(logEntry.getModelName(), ""), promptTokens, completionTokens,
                requested, requested);
        if (claimed == 0) return true;

        BigDecimal charged = consumeAvailable(logEntry.getUserId(), requested, true,
                "API_USAGE", "API_REQUEST", requestId,
                "模型 " + logEntry.getModelName() + " Token 用量扣费", null,
                "api-usage:" + logEntry.getId());
        BigDecimal unpaid = requested.subtract(charged).setScale(3, RoundingMode.HALF_UP);
        String status = unpaid.compareTo(ZERO) > 0 ? "PARTIAL" : "PAID";
        jdbc.update("""
                UPDATE compute_api_usage_charge
                SET charged_card_hours=?, unpaid_card_hours=?, status=?
                WHERE api_request_log_id=?
                """, charged, unpaid, status, logEntry.getId());

        Long supplierUserId = longOrNull(product.get("supplierUserId"));
        if (supplierUserId != null && charged.compareTo(ZERO) > 0) {
            BigDecimal feeRate = settingDecimal("platform_fee_rate", BigDecimal.ZERO);
            BigDecimal supplierIncome = charged.multiply(BigDecimal.ONE.subtract(feeRate))
                    .setScale(3, RoundingMode.DOWN);
            if (supplierIncome.compareTo(ZERO) > 0) {
                credit(supplierUserId, supplierIncome, "SUPPLIER_INCOME", requestId, null,
                        "模型 API 调用结算", null, "api-income:" + logEntry.getId());
            }
        }
        if (unpaid.compareTo(ZERO) > 0) {
            notifyUser(logEntry.getUserId(), "CARD_HOUR_LOW", "卡时不足",
                    "本次模型调用有 " + unpaid.toPlainString() + " 卡时未结算，请及时购买卡时",
                    "API_REQUEST", requestId);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Supplier and product publishing
    // ---------------------------------------------------------------------

    @Transactional
    public Map<String, Object> applySupplier(Long userId, String displayName, String contact, String description) {
        trustService.approvedIdentity(userId);
        requireText(displayName, "供应方名称不能为空");
        jdbc.update("""
                INSERT INTO compute_supplier(user_id, display_name, contact, description, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), contact=VALUES(contact),
                    description=VALUES(description), status='PENDING', rejection_reason='',
                    reviewed_by=NULL, reviewed_at=NULL, update_time=CURRENT_TIMESTAMP
                """, userId, displayName.trim(), safe(contact, 256), safe(description, 1000));
        notifyAdmins("SUPPLIER_APPLIED", "新供应方申请", displayName + " 提交了供应方入驻申请", "SUPPLIER", userId.toString());
        return supplierProfile(userId);
    }

    public Map<String, Object> supplierProfile(Long userId) {
        try {
            return jdbc.queryForMap("""
                    SELECT id, user_id AS userId, display_name AS displayName, contact, description,
                           status, rejection_reason AS rejectionReason, reviewed_at AS reviewedAt,
                           create_time AS createTime
                    FROM compute_supplier WHERE user_id = ?
                    """, userId);
        } catch (EmptyResultDataAccessException e) {
            return Map.of("status", "NONE");
        }
    }

    @Transactional
    public Map<String, Object> createSupplierGpuProduct(Long userId, ProductInput input) {
        Map<String, Object> supplier = supplierProfile(userId);
        if (!"APPROVED".equals(supplier.get("status"))) {
            throw new BizException(403, "供应方尚未通过审核");
        }
        validateGpuProduct(input);
        if (input.nodeId() == null) throw new BizException(400, "请选择已通过验机的 GPU 节点");
        Map<String, Object> node = trustService.requireRunningNode(userId, input.nodeId());
        String verificationType = Objects.toString(node.get("verificationType"), "");
        boolean isTest = "TEST".equals(verificationType) || intValue(node.get("isTest")) == 1;
        if (!Objects.equals(input.gpuModel().trim(), Objects.toString(node.get("gpuModel"), ""))
                || input.gpuMemoryGb() != intValue(node.get("gpuMemoryGb"))) {
            throw new BizException(400, "商品 GPU 型号和显存必须与已验机节点一致");
        }
        if (input.gpuCount() > intValue(node.get("gpuCount"))) {
            throw new BizException(400, "商品 GPU 数量不能超过节点已验机数量");
        }
        jdbc.update("""
                INSERT INTO compute_product(
                    supplier_user_id, node_id, product_type, name, description, region, status,
                    gpu_model, gpu_memory_gb, gpu_count, package_price_card_hours,
                    trade_mode, package_duration_hours, delivery_deadline_hours,
                    delivery_mode, sla_description, is_test)
                VALUES (?, ?, 'GPU', ?, ?, ?, 'PENDING', ?, ?, ?, ?, 'MARKETPLACE_FIXED', ?, ?, ?, ?, ?)
                """, userId, input.nodeId(), input.name().trim(), safe(input.description(), 2000), safe(input.region(), 128),
                input.gpuModel().trim(), input.gpuMemoryGb(), input.gpuCount(), scale3(input.packagePriceCardHours()),
                input.packageDurationHours(), input.deliveryDeadlineHours(), safe(input.deliveryMode(), 64),
                safe(input.slaDescription(), 512), isTest ? 1 : 0);
        Long productId = lastInsertId();
        notifyAdmins("PRODUCT_SUBMITTED", "新 GPU 商品待审核", input.name(), "PRODUCT", productId.toString());
        return getProduct(productId);
    }

    @Transactional
    public Map<String, Object> addProductImage(Long userId, Long productId, MultipartFile image) {
        Map<String, Object> product = getProduct(productId);
        if (!Objects.equals(longOrNull(product.get("supplierUserId")), userId)) {
            throw new BizException(403, "无权上传该商品图片");
        }
        if (!List.of("PENDING", "DRAFT", "REJECTED").contains(Objects.toString(product.get("status")))) {
            throw new BizException(400, "商品审核完成后不能追加图片");
        }
        validateProductImage(image);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM compute_product_image WHERE product_id=?", Long.class, productId);
        if (count != null && count >= 6) throw new BizException(400, "每个商品最多上传 6 张图片");
        try {
            String fileId = documentStorage.store(image.getBytes());
            String mime = Objects.toString(image.getContentType(), "").toLowerCase(Locale.ROOT).contains("png")
                    ? "image/png" : "image/jpeg";
            jdbc.update("INSERT INTO compute_product_image(product_id,file_id,mime_type,sort_order) VALUES (?,?,?,?)",
                    productId, fileId, mime, count == null ? 0 : count.intValue());
            return Map.of("imageId", lastInsertId(), "productId", productId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "商品图片保存失败");
        }
    }

    public ProductImage productImage(Long productId, Long imageId) {
        Map<String, Object> row = queryOne("""
                SELECT i.file_id AS fileId, i.mime_type AS mimeType
                FROM compute_product_image i WHERE i.id=? AND i.product_id=?
                """, imageId, productId);
        return new ProductImage(documentStorage.read(Objects.toString(row.get("fileId"))),
                Objects.toString(row.get("mimeType"), "image/jpeg"));
    }

    @Transactional
    public Map<String, Object> createAdminApiProduct(Long adminUserId, ProductInput input) {
        requireAdmin(adminUserId);
        requireText(input.name(), "商品名称不能为空");
        requireText(input.modelId(), "模型 ID 不能为空");
        if (input.packagePromptTokens() == null || input.packagePromptTokens() <= 0
                || input.packageCompletionTokens() == null || input.packageCompletionTokens() <= 0
                || input.packagePriceCardHours() == null
                || input.packagePriceCardHours().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "输入 Token、输出 Token 和套餐卡时价格必须大于 0");
        }
        validateApiUpstream(input.upstreamStationId(), input.upstreamKeyId());
        Long duplicated = jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_product
                WHERE product_type='API' AND status='PUBLISHED' AND model_id=?
                """, Long.class, input.modelId().trim());
        if (duplicated != null && duplicated > 0) {
            throw new BizException(409, "该模型已有上架中的 API 商品，请先下架旧商品");
        }
        jdbc.update("""
                INSERT INTO compute_product(
                    supplier_user_id, product_type, name, description, region, status, model_id,
                    package_prompt_tokens, package_completion_tokens, package_price_card_hours,
                    upstream_station_id, upstream_key_id, delivery_mode, sla_description,
                    reviewed_by, reviewed_at, published_at)
                VALUES (?, 'API', ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?, ?, ?, 'KOD 平台代理', ?, ?, NOW(), NOW())
                """, input.supplierUserId(), input.name().trim(), safe(input.description(), 2000),
                safe(input.region(), 128), input.modelId().trim(), input.packagePromptTokens(),
                input.packageCompletionTokens(), scale3(input.packagePriceCardHours()),
                input.upstreamStationId(), input.upstreamKeyId(), safe(input.slaDescription(), 512), adminUserId);
        Long productId = lastInsertId();
        audit(adminUserId, "CREATE_API_PRODUCT", "PRODUCT", productId.toString(), input.name());
        return getProduct(productId);
    }

    // ---------------------------------------------------------------------
    // GPU reservations
    // ---------------------------------------------------------------------

    /** 新中介模式：买家购买固定 GPU 套餐，平台只冻结卡时并传递买家公钥。 */
    @Transactional
    public Map<String, Object> createMarketplaceOrder(Long userId, Long productId, String buyerPublicKey,
                                                       boolean autoTopUp) {
        queryOne("SELECT id FROM compute_product WHERE id=? FOR UPDATE", productId);
        Map<String, Object> product = getProduct(productId);
        if (!"GPU".equals(product.get("productType")) || !"PUBLISHED".equals(product.get("status"))
                || !MARKETPLACE_FIXED.equals(product.get("tradeMode"))) {
            throw new BizException(400, "该 GPU 固定套餐当前不可购买");
        }
        String publicKey = normalizeSshPublicKey(buyerPublicKey);
        int durationHours = intValue(product.get("packageDurationHours"));
        int deadlineHours = intValue(product.get("deliveryDeadlineHours"));
        if (durationHours <= 0 || deadlineHours <= 0) throw new BizException(409, "商品交付参数不完整");
        BigDecimal frozen = positiveScale3(decimal(product.get("packagePriceCardHours"), 3), "套餐价格无效");
        LocalDateTime deliveryDeadline = LocalDateTime.now().plusHours(deadlineHours);
        LocalDateTime settlementDeadline = deliveryDeadline.plusHours(24);
        ensureCardHoursForUserAction(userId, frozen, autoTopUp, settlementDeadline);

        String orderNo = "GPM" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO compute_order(order_no,user_id,order_type,product_id,card_hours,status,snapshot_json)
                VALUES (?,?,'GPU_MARKETPLACE',?,?,'FROZEN',
                    JSON_OBJECT('tradeMode','MARKETPLACE_FIXED','durationHours',?,'deliveryDeadlineHours',?,
                                'coverImageId',?))
                """, orderNo, userId, productId, frozen, durationHours, deadlineHours,
                longOrNull(product.get("coverImageId")));
        Long orderId = lastInsertId();
        LocalDateTime placeholderStart = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO compute_reservation(
                    order_id,product_id,buyer_user_id,supplier_user_id,gpu_count,start_time,end_time,
                    unit_rate_snapshot,frozen_card_hours,status,trade_mode,buyer_public_key,delivery_deadline_at)
                VALUES (?,?,?,?,?,?,?,?,?,'PENDING_DELIVERY','MARKETPLACE_FIXED',?,?)
                """, orderId, productId, userId, product.get("supplierUserId"), intValue(product.get("gpuCount")),
                timestamp(placeholderStart), timestamp(placeholderStart.plusHours(durationHours)), frozen, frozen,
                publicKey, timestamp(deliveryDeadline));
        Long reservationId = lastInsertId();
        freezeLots(userId, frozen, settlementDeadline, PURPOSE_RESERVATION, reservationId,
                "GPU 固定套餐担保冻结：" + product.get("name"));
        Long supplierUserId = longOrNull(product.get("supplierUserId"));
        if (supplierUserId != null) {
            notifyUser(supplierUserId, "GPU_MARKETPLACE_ORDER_CREATED", "收到新的 GPU 固定套餐订单",
                    product.get("name") + "；请在 " + deadlineHours + " 小时内与买家商定时间并完成站内交付",
                    "RESERVATION", reservationId.toString());
        }
        return reservationDetail(userId, reservationId);
    }

    @Transactional
    public Map<String, Object> deliverMarketplaceOrder(Long supplierUserId, Long reservationId,
                                                        String sshHost, int sshPort, String sshUsername,
                                                        LocalDateTime actualStart, LocalDateTime actualEnd,
                                                        String deliveryNote) {
        Map<String, Object> reservation = lockReservation(reservationId);
        if (!MARKETPLACE_FIXED.equals(reservation.get("tradeMode"))) {
            throw new BizException(400, "历史预订不能使用新的站内交付接口");
        }
        if (!Objects.equals(longOrNull(reservation.get("supplierUserId")), supplierUserId)) {
            throw new BizException(403, "只有该订单商家可以交付");
        }
        if (!"PENDING_DELIVERY".equals(reservation.get("status"))) {
            throw new BizException(400, "订单当前状态不能交付");
        }
        if (localDateTime(reservation.get("deliveryDeadlineAt")).isBefore(LocalDateTime.now())) {
            throw new BizException(400, "订单已超过承诺交付时限");
        }
        requireText(sshHost, "SSH 地址不能为空");
        requireText(sshUsername, "SSH 临时用户名不能为空");
        if (sshPort <= 0 || sshPort > 65535) throw new BizException(400, "SSH 端口无效");
        if (actualStart == null || actualEnd == null || !actualEnd.isAfter(actualStart)) {
            throw new BizException(400, "请填写双方商定的有效开通和到期时间");
        }
        if (actualStart.isAfter(LocalDateTime.now().plusMinutes(15))) {
            throw new BizException(400, "请在约定开通时间前 15 分钟内再标记交付，避免争议期早于实际可用时间");
        }
        int packageHours = jdbc.queryForObject("SELECT package_duration_hours FROM compute_product WHERE id=?",
                Integer.class, reservation.get("productId"));
        long actualSeconds = Duration.between(actualStart, actualEnd).toSeconds();
        if (actualSeconds != packageHours * 3600L) {
            throw new BizException(400, "交付时长必须与商品固定套餐时长一致");
        }
        String normalizedNote = Objects.toString(deliveryNote, "").trim();
        String upperNote = normalizedNote.toUpperCase(Locale.ROOT);
        if (upperNote.contains("PRIVATE KEY") || upperNote.contains("PASSWORD") || normalizedNote.contains("密码")) {
            throw new BizException(400, "交付说明禁止包含密码或私钥");
        }
        String delivery = "SSH 地址：" + safe(sshHost, 256) + "\n端口：" + sshPort
                + "\n临时用户名：" + safe(sshUsername, 128)
                + (normalizedNote.isBlank() ? "" : "\n交付说明：" + safe(normalizedNote, 1000));
        jdbc.update("""
                UPDATE compute_reservation SET delivery_ciphertext=?,delivered_at=NOW(),status='DELIVERED',
                    start_time=?,end_time=?,auto_confirm_at=DATE_ADD(NOW(),INTERVAL 24 HOUR)
                WHERE id=? AND status='PENDING_DELIVERY'
                """, deliveryCrypto.encrypt(delivery), timestamp(actualStart), timestamp(actualEnd), reservationId);
        jdbc.update("UPDATE compute_order SET status='DELIVERED' WHERE id=?", reservation.get("orderId"));
        notifyUser(longValue(reservation.get("buyerUserId")), "GPU_MARKETPLACE_DELIVERED", "GPU 资源已交付",
                "请核对 SSH 地址和临时账号；无争议将在 24 小时后自动确认并结算",
                "RESERVATION", reservationId.toString());
        return reservationDetail(supplierUserId, reservationId);
    }

    @Transactional
    public Map<String, Object> confirmMarketplaceOrder(Long buyerUserId, Long reservationId) {
        Map<String, Object> reservation = lockReservation(reservationId);
        requireMarketplaceBuyer(buyerUserId, reservation);
        if (!"DELIVERED".equals(reservation.get("status"))) throw new BizException(400, "该订单当前不能确认收货");
        jdbc.update("UPDATE compute_reservation SET buyer_confirmed_at=NOW() WHERE id=?", reservationId);
        completeReservation(reservationId, decimal(reservation.get("frozenCardHours"), 3),
                "BUYER_CONFIRMED", buyerUserId, "买家确认收到资源");
        return reservationDetail(buyerUserId, reservationId);
    }

    @Transactional
    public Map<String, Object> disputeMarketplaceOrder(Long buyerUserId, Long reservationId,
                                                       String reason, String evidence) {
        Map<String, Object> reservation = lockReservation(reservationId);
        requireMarketplaceBuyer(buyerUserId, reservation);
        if (!"DELIVERED".equals(reservation.get("status"))) throw new BizException(400, "只有待确认交付可以发起争议");
        if (localDateTime(reservation.get("autoConfirmAt")).isBefore(LocalDateTime.now())) {
            throw new BizException(400, "24 小时争议期已结束");
        }
        requireText(reason, "发起争议必须填写原因");
        requireText(evidence, "发起争议必须提交可核验的文字证据");
        jdbc.update("""
                UPDATE compute_reservation SET status='DISPUTED',incident_reason=?,dispute_reason=?,
                    dispute_evidence=?,disputed_at=NOW() WHERE id=? AND status='DELIVERED'
                """, safe(reason, 512), safe(reason, 1000), safe(evidence, 2000), reservationId);
        jdbc.update("UPDATE compute_order SET status='DISPUTED' WHERE id=?", reservation.get("orderId"));
        Long supplierUserId = longOrNull(reservation.get("supplierUserId"));
        if (supplierUserId != null) notifyUser(supplierUserId, "GPU_MARKETPLACE_DISPUTED", "GPU 订单发生争议",
                safe(reason, 512) + "；卡时已继续冻结，等待管理员裁决", "RESERVATION", reservationId.toString());
        notifyAdmins("GPU_MARKETPLACE_DISPUTED", "GPU 订单争议待处理", safe(reason, 512),
                "RESERVATION", reservationId.toString());
        return reservationDetail(buyerUserId, reservationId);
    }

    @Transactional
    public Map<String, Object> createReservation(Long userId, Long productId, int gpuCount,
                                                  LocalDateTime start, LocalDateTime end, boolean autoTopUp) {
        // 串行化同一商品的库存检查，避免两个并发订单同时通过并造成 GPU 超卖。
        queryOne("SELECT id FROM compute_product WHERE id=? FOR UPDATE", productId);
        Map<String, Object> product = getProduct(productId);
        if (!"GPU".equals(product.get("productType")) || !"PUBLISHED".equals(product.get("status"))) {
            throw new BizException(400, "该 GPU 商品当前不可预订");
        }
        if (gpuCount <= 0 || gpuCount > intValue(product.get("gpuCount"))) {
            throw new BizException(400, "GPU 数量超出商品库存");
        }
        if (start == null || end == null || !start.isAfter(LocalDateTime.now()) || !end.isAfter(start)) {
            throw new BizException(400, "预订时间必须晚于当前时间，且结束时间晚于开始时间");
        }
        LocalDateTime availableFrom = localDateTime(product.get("availableFrom"));
        LocalDateTime availableTo = localDateTime(product.get("availableTo"));
        if ((availableFrom != null && start.isBefore(availableFrom)) || (availableTo != null && end.isAfter(availableTo))) {
            throw new BizException(400, "预订时间超出商品可用时段");
        }
        Integer occupied = jdbc.queryForObject("""
                SELECT COALESCE(SUM(gpu_count), 0) FROM compute_reservation
                WHERE product_id = ? AND status IN ('PENDING_DELIVERY','CONFIRMED','IN_USE')
                  AND start_time < ? AND end_time > ?
                """, Integer.class, productId, timestamp(end), timestamp(start));
        if ((occupied == null ? 0 : occupied) + gpuCount > intValue(product.get("gpuCount"))) {
            throw new BizException(409, "所选时段 GPU 库存不足");
        }

        BigDecimal hours = BigDecimal.valueOf(Duration.between(start, end).toSeconds())
                .divide(BigDecimal.valueOf(3600), 6, RoundingMode.CEILING);
        BigDecimal rate = scale3(decimal(product.get("pricePerGpuHour"), 3));
        BigDecimal frozen = rate.multiply(hours).multiply(BigDecimal.valueOf(gpuCount))
                .setScale(3, RoundingMode.CEILING);
        if (frozen.compareTo(ZERO) <= 0) throw new BizException(400, "预订价格无效");
        ensureCardHoursForUserAction(userId, frozen, autoTopUp, end);

        String orderNo = "GPU" + UUID.randomUUID().toString().replace("-", "").substring(0, 21).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO compute_order(order_no, user_id, order_type, product_id, card_hours, status, snapshot_json)
                VALUES (?, ?, 'GPU_RESERVATION', ?, ?, 'FROZEN',
                        JSON_OBJECT('unitRate', ?, 'gpuCount', ?, 'coverImageId', ?))
                """, orderNo, userId, productId, frozen, rate.toPlainString(), gpuCount,
                longOrNull(product.get("coverImageId")));
        Long orderId = lastInsertId();
        jdbc.update("""
                INSERT INTO compute_reservation(
                    order_id, product_id, buyer_user_id, supplier_user_id, gpu_count,
                    start_time, end_time, unit_rate_snapshot, frozen_card_hours, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_DELIVERY')
                """, orderId, productId, userId, product.get("supplierUserId"), gpuCount,
                timestamp(start), timestamp(end), rate, frozen);
        Long reservationId = lastInsertId();
        freezeLots(userId, frozen, end, PURPOSE_RESERVATION, reservationId,
                "GPU 预订冻结：" + product.get("name"));
        Long supplierUserId = longOrNull(product.get("supplierUserId"));
        if (supplierUserId != null) {
            notifyUser(supplierUserId, "GPU_RESERVATION_CREATED", "收到新的 GPU 预订",
                    product.get("name") + "，请在开始时间前填写交付信息", "RESERVATION", reservationId.toString());
        } else {
            notifyAdmins("GPU_RESERVATION_CREATED", "平台 GPU 商品收到预订", product.get("name").toString(),
                    "RESERVATION", reservationId.toString());
        }
        return reservationDetail(userId, reservationId);
    }

    @Transactional
    public Map<String, Object> deliverReservation(Long supplierUserId, Long reservationId, String deliveryInfo) {
        Map<String, Object> reservation = lockReservation(reservationId);
        if (!Objects.equals(longOrNull(reservation.get("supplierUserId")), supplierUserId) && !isAdmin(supplierUserId)) {
            throw new BizException(403, "无权交付该预订");
        }
        if (!"PENDING_DELIVERY".equals(reservation.get("status"))) {
            throw new BizException(400, "该预订当前不允许填写交付信息");
        }
        if (!localDateTime(reservation.get("startTime")).isAfter(LocalDateTime.now())) {
            throw new BizException(400, "预订已开始，无法补录交付信息");
        }
        jdbc.update("""
                UPDATE compute_reservation
                SET delivery_ciphertext=?, delivered_at=NOW(), status='CONFIRMED'
                WHERE id=? AND status='PENDING_DELIVERY'
                """, deliveryCrypto.encrypt(deliveryInfo), reservationId);
        notifyUser(longValue(reservation.get("buyerUserId")), "GPU_DELIVERED", "GPU 预订已确认",
                "供应方已提供交付信息，请在预订详情中查看", "RESERVATION", reservationId.toString());
        return reservationDetail(supplierUserId, reservationId);
    }

    @Transactional
    public Map<String, Object> cancelReservation(Long userId, Long reservationId) {
        Map<String, Object> reservation = lockReservation(reservationId);
        if (!Objects.equals(longOrNull(reservation.get("buyerUserId")), userId) && !isAdmin(userId)) {
            throw new BizException(403, "无权取消该预订");
        }
        boolean marketplace = MARKETPLACE_FIXED.equals(reservation.get("tradeMode"));
        if (marketplace && !"PENDING_DELIVERY".equals(reservation.get("status"))) {
            throw new BizException(400, "商家交付后不能直接取消，请在 24 小时内发起争议");
        }
        if (!marketplace && !List.of("PENDING_DELIVERY", "CONFIRMED").contains(Objects.toString(reservation.get("status")))) {
            throw new BizException(400, "预订开始后不可自行取消");
        }
        if (!marketplace && !localDateTime(reservation.get("startTime")).isAfter(LocalDateTime.now())) {
            throw new BizException(400, "预订开始后不可自行取消");
        }
        jdbc.update("UPDATE compute_reservation SET status='CANCELLED', cancelled_at=NOW() WHERE id=?", reservationId);
        jdbc.update("UPDATE compute_order SET status='CANCELLED' WHERE id=?", reservation.get("orderId"));
        releaseFreeze(longValue(reservation.get("buyerUserId")), PURPOSE_RESERVATION, reservationId,
                "GPU 预订取消，卡时解冻");
        Long supplierUserId = longOrNull(reservation.get("supplierUserId"));
        if (supplierUserId != null) {
            notifyUser(supplierUserId, "GPU_RESERVATION_CANCELLED", "GPU 预订已取消",
                    "买方在开始前取消了预订", "RESERVATION", reservationId.toString());
        }
        return Map.of("cancelled", true, "reservationId", reservationId);
    }

    public List<Map<String, Object>> listReservations(Long userId, String role) {
        boolean supplierView = "supplier".equalsIgnoreCase(role);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.order_id AS orderId, r.product_id AS productId,
                       r.buyer_user_id AS buyerUserId, r.supplier_user_id AS supplierUserId,
                        r.gpu_count AS gpuCount, r.start_time AS startTime, r.end_time AS endTime,
                        r.unit_rate_snapshot AS unitRateSnapshot, r.frozen_card_hours AS frozenCardHours,
                        r.settled_card_hours AS settledCardHours, r.status,
                        r.trade_mode AS tradeMode, r.buyer_public_key AS buyerPublicKey,
                        r.delivery_deadline_at AS deliveryDeadlineAt, r.auto_confirm_at AS autoConfirmAt,
                        r.buyer_confirmed_at AS buyerConfirmedAt, r.dispute_reason AS disputeReason,
                        r.dispute_evidence AS disputeEvidence, r.disputed_at AS disputedAt,
                       r.status_before_incident AS statusBeforeIncident, r.incident_reason AS incidentReason,
                       r.resolution_type AS resolutionType, r.resolution_card_hours AS resolutionCardHours,
                       r.delivery_ciphertext AS deliveryCiphertext, r.delivered_at AS deliveredAt,
                       r.create_time AS createTime, p.name AS productName, p.gpu_model AS gpuModel,
                       COALESCE(
                           CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(o.snapshot_json, '$.coverImageId')), 'null') AS UNSIGNED),
                           (SELECT i.id FROM compute_product_image i
                            WHERE i.product_id=r.product_id ORDER BY i.sort_order,i.id LIMIT 1)
                       ) AS coverImageId,
                        buyer.email AS buyerEmail, supplier.email AS supplierEmail
                FROM compute_reservation r
                JOIN compute_order o ON o.id = r.order_id
                JOIN compute_product p ON p.id = r.product_id
                JOIN sys_user buyer ON buyer.id = r.buyer_user_id
                LEFT JOIN sys_user supplier ON supplier.id=r.supplier_user_id
                WHERE %s = ? ORDER BY r.id DESC LIMIT 200
                """.formatted(supplierView ? "r.supplier_user_id" : "r.buyer_user_id"), userId);
        rows.forEach(row -> {
            Object ciphertext = row.remove("deliveryCiphertext");
            row.put("deliveryInfo", ciphertext == null ? "" : deliveryCrypto.decrypt(ciphertext.toString()));
        });
        return rows;
    }

    private Map<String, Object> reservationDetail(Long viewerUserId, Long reservationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.order_id AS orderId, r.product_id AS productId,
                       r.buyer_user_id AS buyerUserId, r.supplier_user_id AS supplierUserId,
                       r.gpu_count AS gpuCount, r.start_time AS startTime, r.end_time AS endTime,
                       r.unit_rate_snapshot AS unitRateSnapshot, r.frozen_card_hours AS frozenCardHours,
                        r.settled_card_hours AS settledCardHours, r.status,
                        r.trade_mode AS tradeMode, r.buyer_public_key AS buyerPublicKey,
                        r.delivery_deadline_at AS deliveryDeadlineAt, r.auto_confirm_at AS autoConfirmAt,
                        r.buyer_confirmed_at AS buyerConfirmedAt, r.dispute_reason AS disputeReason,
                        r.dispute_evidence AS disputeEvidence, r.disputed_at AS disputedAt,
                       r.status_before_incident AS statusBeforeIncident, r.incident_reason AS incidentReason,
                       r.resolution_type AS resolutionType, r.resolution_card_hours AS resolutionCardHours,
                        r.delivery_ciphertext AS deliveryCiphertext, r.delivered_at AS deliveredAt,
                        r.create_time AS createTime, p.name AS productName, p.gpu_model AS gpuModel,
                        COALESCE(
                            CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(o.snapshot_json, '$.coverImageId')), 'null') AS UNSIGNED),
                            (SELECT i.id FROM compute_product_image i
                             WHERE i.product_id=r.product_id ORDER BY i.sort_order,i.id LIMIT 1)
                        ) AS coverImageId,
                        buyer.email AS buyerEmail, supplier.email AS supplierEmail
                FROM compute_reservation r
                JOIN compute_order o ON o.id=r.order_id
                JOIN compute_product p ON p.id=r.product_id
                JOIN sys_user buyer ON buyer.id=r.buyer_user_id
                LEFT JOIN sys_user supplier ON supplier.id=r.supplier_user_id WHERE r.id=?
                """, reservationId);
        if (rows.isEmpty()) throw new BizException(404, "GPU 预订不存在");
        Map<String, Object> row = rows.get(0);
        long buyer = longValue(row.get("buyerUserId"));
        Long supplier = longOrNull(row.get("supplierUserId"));
        if (viewerUserId != buyer && !Objects.equals(viewerUserId, supplier) && !isAdmin(viewerUserId)) {
            throw new BizException(403, "无权查看该预订");
        }
        Object ciphertext = row.remove("deliveryCiphertext");
        row.put("deliveryInfo", ciphertext == null ? "" : deliveryCrypto.decrypt(ciphertext.toString()));
        return row;
    }

    // ---------------------------------------------------------------------
    // Transfers
    // ---------------------------------------------------------------------

    @Transactional
    public Map<String, Object> createTransfer(Long senderUserId, String recipientEmail,
                                              BigDecimal rawAmount, String message, boolean autoTopUp) {
        BigDecimal amount = positiveScale3(rawAmount, "转让卡时必须大于 0");
        Map<String, Object> recipient = findUserByEmail(recipientEmail);
        long recipientUserId = longValue(recipient.get("id"));
        if (recipientUserId == senderUserId) throw new BizException(400, "不能转让给自己");
        validateTransferScope(senderUserId, recipientUserId);

        BigDecimal threshold = settingDecimal("transfer_review_threshold",
                properties.getDefaultTransferReviewThreshold());
        String status = amount.compareTo(threshold) >= 0 ? "PENDING_REVIEW" : "PENDING_RECIPIENT";
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        ensureCardHoursForUserAction(senderUserId, amount, autoTopUp, expiresAt);
        String transferNo = "TR" + UUID.randomUUID().toString().replace("-", "").substring(0, 23).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO compute_transfer(
                    transfer_no, sender_user_id, recipient_user_id, amount, message, status, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, transferNo, senderUserId, recipientUserId, amount, safe(message, 512), status,
                timestamp(expiresAt));
        Long transferId = lastInsertId();
        freezeLots(senderUserId, amount, expiresAt, PURPOSE_TRANSFER, transferId, "卡时转让冻结");
        if ("PENDING_REVIEW".equals(status)) {
            notifyAdmins("TRANSFER_REVIEW", "大额卡时转让待审核", transferNo + "：" + amount + " 卡时",
                    "TRANSFER", transferId.toString());
        } else {
            notifyUser(recipientUserId, "TRANSFER_RECEIVED", "收到卡时转让",
                    "你收到 " + amount + " 卡时的待确认转让", "TRANSFER", transferId.toString());
        }
        return transferDetail(senderUserId, transferId);
    }

    @Transactional
    public Map<String, Object> acceptTransfer(Long recipientUserId, Long transferId) {
        Map<String, Object> transfer = lockTransfer(transferId);
        if (longValue(transfer.get("recipientUserId")) != recipientUserId) {
            throw new BizException(403, "无权接收该转让");
        }
        if (!"PENDING_RECIPIENT".equals(transfer.get("status"))) {
            throw new BizException(400, "该转让当前不可接收");
        }
        if (!localDateTime(transfer.get("expiresAt")).isAfter(LocalDateTime.now())) {
            expireTransfer(transfer);
            throw new BizException(400, "转让已过期");
        }
        long senderUserId = longValue(transfer.get("senderUserId"));
        BigDecimal amount = decimal(transfer.get("amount"), 3);
        moveFrozenLots(senderUserId, recipientUserId, PURPOSE_TRANSFER, transferId,
                "转让 " + transfer.get("transferNo"));
        jdbc.update("UPDATE compute_transfer SET status='COMPLETED', accepted_at=NOW() WHERE id=?", transferId);
        notifyUser(senderUserId, "TRANSFER_ACCEPTED", "卡时转让已接收",
                amount + " 卡时已由接收方确认", "TRANSFER", transferId.toString());
        return transferDetail(recipientUserId, transferId);
    }

    @Transactional
    public Map<String, Object> cancelTransfer(Long senderUserId, Long transferId) {
        Map<String, Object> transfer = lockTransfer(transferId);
        if (longValue(transfer.get("senderUserId")) != senderUserId) {
            throw new BizException(403, "无权撤回该转让");
        }
        if (!List.of("PENDING_REVIEW", "PENDING_RECIPIENT").contains(Objects.toString(transfer.get("status")))) {
            throw new BizException(400, "该转让当前不可撤回");
        }
        jdbc.update("UPDATE compute_transfer SET status='CANCELLED' WHERE id=?", transferId);
        releaseFreeze(senderUserId, PURPOSE_TRANSFER, transferId, "卡时转让撤回");
        notifyUser(longValue(transfer.get("recipientUserId")), "TRANSFER_CANCELLED", "卡时转让已撤回",
                "转出方撤回了待接收转让", "TRANSFER", transferId.toString());
        return Map.of("cancelled", true, "transferId", transferId);
    }

    public List<Map<String, Object>> listTransfers(Long userId) {
        return jdbc.queryForList("""
                SELECT t.id, t.transfer_no AS transferNo, t.sender_user_id AS senderUserId,
                       t.recipient_user_id AS recipientUserId, t.amount, t.message, t.status,
                       t.review_reason AS reviewReason, t.expires_at AS expiresAt,
                       t.create_time AS createTime, sender.email AS senderEmail,
                       recipient.email AS recipientEmail
                FROM compute_transfer t
                JOIN sys_user sender ON sender.id=t.sender_user_id
                JOIN sys_user recipient ON recipient.id=t.recipient_user_id
                WHERE t.sender_user_id=? OR t.recipient_user_id=?
                ORDER BY t.id DESC LIMIT 200
                """, userId, userId);
    }

    private Map<String, Object> transferDetail(Long viewerUserId, Long transferId) {
        Map<String, Object> transfer = lockTransfer(transferId);
        if (viewerUserId != longValue(transfer.get("senderUserId"))
                && viewerUserId != longValue(transfer.get("recipientUserId")) && !isAdmin(viewerUserId)) {
            throw new BizException(403, "无权查看该转让");
        }
        return transfer;
    }

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------

    public List<Map<String, Object>> listNotifications(Long userId) {
        return jdbc.queryForList("""
                SELECT id, notification_type AS notificationType, title, content,
                       reference_type AS referenceType, reference_id AS referenceId,
                       is_read AS isRead, create_time AS createTime, read_time AS readTime
                FROM compute_notification WHERE user_id=? ORDER BY id DESC LIMIT 200
                """, userId);
    }

    public void markNotificationRead(Long userId, Long notificationId) {
        jdbc.update("UPDATE compute_notification SET is_read=1, read_time=NOW() WHERE id=? AND user_id=?",
                notificationId, userId);
    }

    // ---------------------------------------------------------------------
    // Admin operations
    // ---------------------------------------------------------------------

    public Map<String, Object> adminOverview(Long adminUserId) {
        requireAdmin(adminUserId);
        Map<String, Object> result = new HashMap<>();
        result.put("accounts", scalar("SELECT COUNT(*) FROM compute_account"));
        result.put("identitiesPending", scalar("SELECT COUNT(*) FROM compute_identity_verification WHERE status='PENDING'"));
        result.put("suppliersPending", scalar("SELECT COUNT(*) FROM compute_supplier WHERE status='PENDING'"));
        result.put("nodesPending", scalar("SELECT COUNT(*) FROM compute_gpu_node WHERE status='PENDING'"));
        result.put("nodesPendingAction", scalar("SELECT COUNT(*) FROM compute_gpu_node WHERE status='PENDING_ACTION'"));
        result.put("productsPending", scalar("SELECT COUNT(*) FROM compute_product WHERE status='PENDING'"));
        result.put("transfersPending", scalar("SELECT COUNT(*) FROM compute_transfer WHERE status='PENDING_REVIEW'"));
        result.put("reservationsActive", scalar("""
                SELECT COUNT(*) FROM compute_reservation
                WHERE status IN ('PENDING_DELIVERY','CONFIRMED','IN_USE','DELIVERED','DISPUTED')
                """));
        result.put("circulatingCardHours", jdbc.queryForObject(
                "SELECT COALESCE(SUM(available_card_hours + frozen_card_hours),0) FROM compute_account",
                BigDecimal.class));
        result.put("transferReviewThreshold", settingDecimal("transfer_review_threshold",
                properties.getDefaultTransferReviewThreshold()));
        result.put("platformFeeRate", settingDecimal("platform_fee_rate", BigDecimal.ZERO));
        result.put("usdCnyRate", settingDecimal("usd_cny_rate", properties.getDefaultUsdCnyRate()));
        return result;
    }

    @Transactional
    public Map<String, Object> updateAdminSettings(Long adminUserId, BigDecimal rawTransferThreshold,
                                                    BigDecimal rawPlatformFeeRate, BigDecimal rawUsdCnyRate) {
        requireAdmin(adminUserId);
        BigDecimal transferThreshold = positiveScale3(rawTransferThreshold, "大额转让审核阈值必须大于 0");
        if (rawPlatformFeeRate == null || rawPlatformFeeRate.compareTo(BigDecimal.ZERO) != 0) {
            throw new BizException(400, "第一版不收平台佣金，服务费必须为 0");
        }
        BigDecimal platformFeeRate = BigDecimal.ZERO.setScale(6, RoundingMode.UNNECESSARY);
        BigDecimal usdCnyRate = positiveScale(rawUsdCnyRate, 4, "美元兑人民币汇率必须大于 0");
        upsertSetting("transfer_review_threshold", transferThreshold.toPlainString(), adminUserId);
        upsertSetting("platform_fee_rate", platformFeeRate.toPlainString(), adminUserId);
        upsertSetting("usd_cny_rate", usdCnyRate.toPlainString(), adminUserId);
        audit(adminUserId, "UPDATE_COMPUTE_SETTINGS", "SETTING", "compute",
                "transferThreshold=" + transferThreshold + ",platformFeeRate=" + platformFeeRate
                        + ",usdCnyRate=" + usdCnyRate);
        return adminOverview(adminUserId);
    }

    public List<Map<String, Object>> adminSuppliers(Long adminUserId) {
        requireAdmin(adminUserId);
        return jdbc.queryForList("""
                SELECT s.id, s.user_id AS userId, u.email, s.display_name AS displayName,
                       s.contact, s.description, s.status, s.rejection_reason AS rejectionReason,
                       s.create_time AS createTime
                FROM compute_supplier s JOIN sys_user u ON u.id=s.user_id ORDER BY s.id DESC
                """);
    }

    @Transactional
    public Map<String, Object> reviewSupplier(Long adminUserId, Long supplierId, boolean approved, String reason) {
        requireAdmin(adminUserId);
        Map<String, Object> supplier = queryOne("""
                SELECT id, user_id AS userId, display_name AS displayName, status
                FROM compute_supplier WHERE id=? FOR UPDATE
                """, supplierId);
        if (longValue(supplier.get("userId")) == adminUserId) {
            throw new BizException(403, "供应方入驻不得由本人审核，请使用另一名管理员账号");
        }
        String status = approved ? "APPROVED" : "REJECTED";
        if (!approved) requireText(reason, "拒绝时必须填写原因");
        jdbc.update("""
                UPDATE compute_supplier SET status=?, rejection_reason=?, reviewed_by=?, reviewed_at=NOW()
                WHERE id=?
                """, status, approved ? "" : safe(reason, 512), adminUserId, supplierId);
        long userId = longValue(supplier.get("userId"));
        notifyUser(userId, "SUPPLIER_REVIEWED", approved ? "供应方申请已通过" : "供应方申请未通过",
                approved ? "你现在可以发布 GPU 商品" : safe(reason, 512), "SUPPLIER", supplierId.toString());
        audit(adminUserId, "REVIEW_SUPPLIER", "SUPPLIER", supplierId.toString(), status + ":" + safe(reason, 512));
        return supplierProfile(userId);
    }

    public List<Map<String, Object>> adminProducts(Long adminUserId) {
        requireAdmin(adminUserId);
        return listProducts(null, true, null);
    }

    @Transactional
    public Map<String, Object> configureApiProductUpstream(Long adminUserId, Long productId,
                                                           Long stationId, Long keyId) {
        requireAdmin(adminUserId);
        Map<String, Object> product = getProduct(productId);
        if (!"API".equals(product.get("productType"))) throw new BizException(400, "只有 API 套餐需要配置零售站");
        validateApiUpstream(stationId, keyId);
        jdbc.update("UPDATE compute_product SET upstream_station_id=?, upstream_key_id=? WHERE id=?",
                stationId, keyId, productId);
        audit(adminUserId, "CONFIGURE_API_UPSTREAM", "PRODUCT", productId.toString(),
                "stationId=" + stationId + ",keyId=" + keyId);
        return getProduct(productId);
    }

    @Transactional
    public Map<String, Object> reviewProduct(Long adminUserId, Long productId, boolean approved, String reason) {
        requireAdmin(adminUserId);
        Map<String, Object> product = getProduct(productId);
        Long supplierUserId = longOrNull(product.get("supplierUserId"));
        if (supplierUserId != null && supplierUserId.equals(adminUserId)) {
            throw new BizException(403, "商品不得由供应方本人审核，请使用另一名管理员账号");
        }
        if (!approved) requireText(reason, "拒绝时必须填写原因");
        if (approved && "GPU".equals(product.get("productType"))) {
            trustService.requireRunningNode(supplierUserId, longOrNull(product.get("nodeId")));
        }
        String status = approved ? "PUBLISHED" : "REJECTED";
        jdbc.update("""
                UPDATE compute_product SET status=?, rejection_reason=?, reviewed_by=?, reviewed_at=NOW(),
                    published_at=CASE WHEN ?='PUBLISHED' THEN NOW() ELSE published_at END
                WHERE id=?
                """, status, approved ? "" : safe(reason, 512), adminUserId, status, productId);
        if (supplierUserId != null) {
            notifyUser(supplierUserId, "PRODUCT_REVIEWED", approved ? "商品已上架" : "商品审核未通过",
                    approved ? Objects.toString(product.get("name")) : safe(reason, 512), "PRODUCT", productId.toString());
        }
        audit(adminUserId, "REVIEW_PRODUCT", "PRODUCT", productId.toString(), status + ":" + safe(reason, 512));
        return getProduct(productId);
    }

    public List<Map<String, Object>> adminTransfers(Long adminUserId) {
        requireAdmin(adminUserId);
        return jdbc.queryForList("""
                SELECT t.id, t.transfer_no AS transferNo, t.amount, t.message, t.status,
                       t.create_time AS createTime, sender.email AS senderEmail,
                       recipient.email AS recipientEmail
                FROM compute_transfer t
                JOIN sys_user sender ON sender.id=t.sender_user_id
                JOIN sys_user recipient ON recipient.id=t.recipient_user_id
                WHERE t.status='PENDING_REVIEW' ORDER BY t.id ASC
                """);
    }

    @Transactional
    public Map<String, Object> reviewTransfer(Long adminUserId, Long transferId, boolean approved, String reason) {
        requireAdmin(adminUserId);
        Map<String, Object> transfer = lockTransfer(transferId);
        if (!"PENDING_REVIEW".equals(transfer.get("status"))) {
            throw new BizException(400, "该转让不在待审核状态");
        }
        if (!approved) requireText(reason, "拒绝时必须填写原因");
        if (approved) {
            jdbc.update("""
                    UPDATE compute_transfer SET status='PENDING_RECIPIENT', review_reason='',
                        reviewed_by=?, reviewed_at=NOW() WHERE id=?
                    """, adminUserId, transferId);
            notifyUser(longValue(transfer.get("recipientUserId")), "TRANSFER_RECEIVED", "收到卡时转让",
                    "管理员已通过大额转让审核，请确认接收", "TRANSFER", transferId.toString());
        } else {
            jdbc.update("""
                    UPDATE compute_transfer SET status='REJECTED', review_reason=?,
                        reviewed_by=?, reviewed_at=NOW() WHERE id=?
                    """, safe(reason, 512), adminUserId, transferId);
            releaseFreeze(longValue(transfer.get("senderUserId")), PURPOSE_TRANSFER, transferId,
                    "大额转让审核未通过，卡时解冻");
            notifyUser(longValue(transfer.get("senderUserId")), "TRANSFER_REJECTED", "卡时转让审核未通过",
                    safe(reason, 512), "TRANSFER", transferId.toString());
        }
        audit(adminUserId, "REVIEW_TRANSFER", "TRANSFER", transferId.toString(),
                (approved ? "APPROVED:" : "REJECTED:") + safe(reason, 512));
        return transferDetail(adminUserId, transferId);
    }

    public List<Map<String, Object>> adminReservations(Long adminUserId) {
        requireAdmin(adminUserId);
        return jdbc.queryForList("""
                SELECT r.id, r.order_id AS orderId, r.product_id AS productId,
                       r.buyer_user_id AS buyerUserId, r.supplier_user_id AS supplierUserId,
                       buyer.email AS buyerEmail, supplier.email AS supplierEmail,
                       r.gpu_count AS gpuCount, r.start_time AS startTime, r.end_time AS endTime,
                       r.frozen_card_hours AS frozenCardHours, r.settled_card_hours AS settledCardHours,
                       r.status, r.trade_mode AS tradeMode, r.buyer_public_key AS buyerPublicKey,
                       r.delivery_deadline_at AS deliveryDeadlineAt, r.auto_confirm_at AS autoConfirmAt,
                       r.dispute_reason AS disputeReason, r.dispute_evidence AS disputeEvidence,
                       r.disputed_at AS disputedAt, r.status_before_incident AS statusBeforeIncident,
                       r.incident_reason AS incidentReason, r.resolution_type AS resolutionType,
                       r.resolution_card_hours AS resolutionCardHours, r.create_time AS createTime,
                       p.name AS productName, p.gpu_model AS gpuModel, p.node_id AS nodeId,
                       COALESCE(
                           CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(o.snapshot_json, '$.coverImageId')), 'null') AS UNSIGNED),
                           (SELECT i.id FROM compute_product_image i
                            WHERE i.product_id=r.product_id ORDER BY i.sort_order,i.id LIMIT 1)
                       ) AS coverImageId,
                       n.node_name AS nodeName, n.status AS nodeStatus
                FROM compute_reservation r
                JOIN compute_order o ON o.id=r.order_id
                JOIN compute_product p ON p.id=r.product_id
                JOIN sys_user buyer ON buyer.id=r.buyer_user_id
                LEFT JOIN sys_user supplier ON supplier.id=r.supplier_user_id
                LEFT JOIN compute_gpu_node n ON n.id=p.node_id
                ORDER BY CASE WHEN r.status IN ('DISPUTED','EXCEPTION_PENDING') THEN 0 ELSE 1 END, r.id DESC LIMIT 300
                """);
    }

    @Transactional
    public Map<String, Object> settleReservationNow(Long adminUserId, Long reservationId) {
        requireAdmin(adminUserId);
        Map<String, Object> reservation = lockReservation(reservationId);
        if (!List.of("CONFIRMED", "IN_USE").contains(Objects.toString(reservation.get("status")))) {
            throw new BizException(400, "只有已确认或使用中的正常订单可以补偿结算");
        }
        if (localDateTime(reservation.get("endTime")).isAfter(LocalDateTime.now())) {
            throw new BizException(400, "订单尚未到达预订结束时间");
        }
        settleReservation(reservationId);
        audit(adminUserId, "SETTLE_RESERVATION_NOW", "RESERVATION", reservationId.toString(), "manual compensation");
        return reservationDetail(adminUserId, reservationId);
    }

    @Transactional
    public Map<String, Object> resolveReservation(Long adminUserId, Long reservationId,
                                                   String rawResolution, BigDecimal rawActualCardHours,
                                                   String reason) {
        requireAdmin(adminUserId);
        Map<String, Object> reservation = lockReservation(reservationId);
        if (!List.of("EXCEPTION_PENDING", "DISPUTED").contains(Objects.toString(reservation.get("status")))) {
            throw new BizException(400, "该订单不在异常待处理状态");
        }
        String resolution = Objects.toString(rawResolution, "").trim().toUpperCase(Locale.ROOT);
        if (!List.of("FULL_REFUND", "ACTUAL_USAGE", "FULL_SETTLEMENT").contains(resolution)) {
            throw new BizException(400, "异常订单只能全额退款、按实际使用结算或按原订单结算");
        }
        requireText(reason, "处理异常订单时必须填写原因");
        BigDecimal frozen = decimal(reservation.get("frozenCardHours"), 3);
        BigDecimal settlement;
        if ("FULL_REFUND".equals(resolution)) {
            settlement = ZERO;
        } else if ("FULL_SETTLEMENT".equals(resolution)) {
            settlement = frozen;
        } else {
            settlement = positiveScale3(rawActualCardHours, "实际结算卡时必须大于 0");
            if (settlement.compareTo(frozen) > 0) throw new BizException(400, "实际结算卡时不能超过订单冻结卡时");
        }
        completeReservation(reservationId, settlement, resolution, adminUserId, safe(reason, 512));
        audit(adminUserId, "RESOLVE_EXCEPTION_RESERVATION", "RESERVATION", reservationId.toString(),
                resolution + ":" + settlement + ":" + safe(reason, 512));
        return reservationDetail(adminUserId, reservationId);
    }

    @Transactional
    public Map<String, Object> grantCardHours(Long adminUserId, String recipientEmail,
                                              BigDecimal rawAmount, LocalDateTime expiresAt, String reason) {
        requireAdmin(adminUserId);
        requireText(reason, "发放测试卡时必须填写原因");
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BizException(400, "测试卡时有效期必须晚于当前时间");
        }
        BigDecimal amount = positiveScale3(rawAmount, "发放数量必须大于 0");
        Map<String, Object> recipient = findUserByEmail(recipientEmail);
        long recipientUserId = longValue(recipient.get("id"));
        String grantRef = "GR" + UUID.randomUUID().toString().replace("-", "").substring(0, 22).toUpperCase(Locale.ROOT);
        credit(recipientUserId, amount, "ADMIN_GRANT", grantRef, expiresAt, safe(reason, 512), adminUserId,
                "grant:" + grantRef);
        notifyUser(recipientUserId, "CARD_HOUR_GRANTED", "收到测试卡时",
                "管理员发放 " + amount + " 卡时：" + safe(reason, 512), "GRANT", grantRef);
        audit(adminUserId, "GRANT_CARD_HOURS", "USER", Long.toString(recipientUserId),
                amount + ":" + safe(reason, 512));
        return getAccount(recipientUserId);
    }

    // ---------------------------------------------------------------------
    // Scheduled settlement
    // ---------------------------------------------------------------------

    @Transactional
    public void advanceScheduledWork() {
        List<Map<String, Object>> marketplaceDeliveryTimeouts = jdbc.queryForList("""
                SELECT id FROM compute_reservation
                WHERE trade_mode='MARKETPLACE_FIXED' AND status='PENDING_DELIVERY'
                  AND delivery_deadline_at <= NOW() ORDER BY id LIMIT 100
                FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> item : marketplaceDeliveryTimeouts) {
            long id = longValue(item.get("id"));
            Map<String, Object> reservation = lockReservation(id);
            jdbc.update("UPDATE compute_reservation SET status='CANCELLED',cancelled_at=NOW() WHERE id=?", id);
            jdbc.update("UPDATE compute_order SET status='CANCELLED' WHERE id=?", reservation.get("orderId"));
            releaseFreeze(longValue(reservation.get("buyerUserId")), PURPOSE_RESERVATION, id,
                    "商家超过承诺时限未交付，卡时全额退回");
            notifyUser(longValue(reservation.get("buyerUserId")), "GPU_MARKETPLACE_DELIVERY_TIMEOUT",
                    "GPU 固定套餐已自动取消", "商家超过承诺交付时限，冻结卡时已全额退回",
                    "RESERVATION", Long.toString(id));
        }

        List<Map<String, Object>> deliveryTimeouts = jdbc.queryForList("""
                SELECT id FROM compute_reservation
                WHERE trade_mode='LEGACY_RESERVATION' AND status='PENDING_DELIVERY'
                  AND start_time <= NOW() ORDER BY id LIMIT 100
                FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> item : deliveryTimeouts) {
            long id = longValue(item.get("id"));
            Map<String, Object> reservation = lockReservation(id);
            jdbc.update("UPDATE compute_reservation SET status='CANCELLED', cancelled_at=NOW() WHERE id=?", id);
            jdbc.update("UPDATE compute_order SET status='CANCELLED' WHERE id=?", reservation.get("orderId"));
            releaseFreeze(longValue(reservation.get("buyerUserId")), PURPOSE_RESERVATION, id,
                    "供应方未按时交付，预订自动取消");
            notifyUser(longValue(reservation.get("buyerUserId")), "GPU_DELIVERY_TIMEOUT", "GPU 预订已自动取消",
                    "供应方未在开始时间前交付，冻结卡时已退回", "RESERVATION", Long.toString(id));
        }

        jdbc.update("""
                UPDATE compute_reservation SET status='IN_USE'
                WHERE trade_mode='LEGACY_RESERVATION' AND status='CONFIRMED'
                  AND start_time <= NOW() AND end_time > NOW()
                """);

        List<Map<String, Object>> completed = jdbc.queryForList("""
                SELECT id FROM compute_reservation
                WHERE trade_mode='LEGACY_RESERVATION' AND status IN ('CONFIRMED','IN_USE')
                  AND end_time <= NOW() ORDER BY id LIMIT 100
                FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> item : completed) {
            settleReservation(longValue(item.get("id")));
        }

        List<Map<String, Object>> autoConfirmed = jdbc.queryForList("""
                SELECT id FROM compute_reservation
                WHERE trade_mode='MARKETPLACE_FIXED' AND status='DELIVERED'
                  AND auto_confirm_at <= NOW() ORDER BY id LIMIT 100
                FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> item : autoConfirmed) {
            long id = longValue(item.get("id"));
            Map<String, Object> reservation = lockReservation(id);
            completeReservation(id, decimal(reservation.get("frozenCardHours"), 3),
                    "AUTO_CONFIRM_24H", null, "交付后 24 小时内无争议，系统自动确认");
        }

        List<Map<String, Object>> expiredTransfers = jdbc.queryForList("""
                SELECT id FROM compute_transfer
                WHERE status IN ('PENDING_REVIEW','PENDING_RECIPIENT') AND expires_at <= NOW()
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> item : expiredTransfers) {
            expireTransfer(lockTransfer(longValue(item.get("id"))));
        }

        expireAvailableLots();
        jdbc.update("""
                UPDATE compute_reservation SET delivery_ciphertext=NULL
                WHERE delivery_ciphertext IS NOT NULL AND delivered_at < DATE_SUB(NOW(), INTERVAL 30 DAY)
                """);
    }

    private void settleReservation(Long reservationId) {
        Map<String, Object> reservation = lockReservation(reservationId);
        if (!List.of("CONFIRMED", "IN_USE").contains(Objects.toString(reservation.get("status")))) return;
        BigDecimal frozen = decimal(reservation.get("frozenCardHours"), 3);
        completeReservation(reservationId, frozen, "AUTO_SETTLEMENT", null, "预订时段正常结束");
    }

    private void completeReservation(Long reservationId, BigDecimal rawSettlement, String resolution,
                                     Long adminUserId, String reason) {
        Map<String, Object> reservation = lockReservation(reservationId);
        String currentStatus = Objects.toString(reservation.get("status"));
        if (!(List.of("CONFIRMED", "IN_USE", "DELIVERED").contains(currentStatus)
                || (List.of("EXCEPTION_PENDING", "DISPUTED").contains(currentStatus)
                    && List.of("FULL_REFUND", "ACTUAL_USAGE", "FULL_SETTLEMENT").contains(resolution)))) {
            throw new BizException(400, "订单当前状态不能结算");
        }
        long buyerUserId = longValue(reservation.get("buyerUserId"));
        BigDecimal frozen = decimal(reservation.get("frozenCardHours"), 3);
        BigDecimal settlement = scale3(rawSettlement);
        if (settlement.compareTo(ZERO) < 0 || settlement.compareTo(frozen) > 0) {
            throw new BizException(400, "结算卡时不在订单冻结范围内");
        }
        resolveFrozenReservation(buyerUserId, reservationId, settlement,
                "GPU 订单结算：" + safe(reason, 512), adminUserId);
        BigDecimal feeRate = MARKETPLACE_FIXED.equals(reservation.get("tradeMode"))
                ? BigDecimal.ZERO : settingDecimal("platform_fee_rate", BigDecimal.ZERO);
        BigDecimal fee = settlement.multiply(feeRate).setScale(3, RoundingMode.DOWN);
        BigDecimal supplierIncome = settlement.subtract(fee).setScale(3, RoundingMode.HALF_UP);
        Long supplierUserId = longOrNull(reservation.get("supplierUserId"));
        if (supplierUserId != null && supplierIncome.compareTo(ZERO) > 0) {
            credit(supplierUserId, supplierIncome, "GPU_RENTAL_INCOME", reservationId.toString(), null,
                    MARKETPLACE_FIXED.equals(reservation.get("tradeMode"))
                            ? "GPU 固定套餐交易收入" : "GPU 预订结算收入",
                    adminUserId, "gpu-income:" + reservationId);
            notifyUser(supplierUserId, "GPU_SETTLED", MARKETPLACE_FIXED.equals(reservation.get("tradeMode"))
                            ? "GPU 固定套餐已结算" : "GPU 预订已结算",
                    "收入 " + supplierIncome + " 卡时", "RESERVATION", reservationId.toString());
        }
        String finalStatus = settlement.compareTo(ZERO) == 0 ? "REFUNDED" : "COMPLETED";
        jdbc.update("""
                UPDATE compute_reservation SET status=?, settled_card_hours=?,
                    platform_fee=?, completed_at=NOW(), resolution_type=?, resolution_card_hours=?,
                    resolved_by=?, resolved_at=CASE WHEN ? IS NULL THEN resolved_at ELSE NOW() END
                WHERE id=?
                """, finalStatus, settlement, fee, resolution, settlement, adminUserId, adminUserId, reservationId);
        jdbc.update("UPDATE compute_order SET status=? WHERE id=?", finalStatus, reservation.get("orderId"));
        BigDecimal refunded = frozen.subtract(settlement).setScale(3, RoundingMode.HALF_UP);
        notifyUser(buyerUserId, settlement.compareTo(ZERO) == 0 ? "GPU_REFUNDED" : "GPU_SETTLED",
                settlement.compareTo(ZERO) == 0 ? "GPU 订单已全额退款" : "GPU 预订已完成",
                "结算 " + settlement + " 卡时，退回 " + refunded + " 卡时；" + safe(reason, 512),
                "RESERVATION", reservationId.toString());
    }

    private void expireTransfer(Map<String, Object> transfer) {
        long transferId = longValue(transfer.get("id"));
        String status = Objects.toString(transfer.get("status"));
        if (!List.of("PENDING_REVIEW", "PENDING_RECIPIENT").contains(status)) return;
        jdbc.update("UPDATE compute_transfer SET status='EXPIRED' WHERE id=?", transferId);
        releaseFreeze(longValue(transfer.get("senderUserId")), PURPOSE_TRANSFER, transferId,
                "卡时转让过期，冻结额度退回");
        notifyUser(longValue(transfer.get("senderUserId")), "TRANSFER_EXPIRED", "卡时转让已过期",
                "接收方未在有效期内确认", "TRANSFER", Long.toString(transferId));
    }

    private void expireAvailableLots() {
        List<Map<String, Object>> lots = jdbc.queryForList("""
                SELECT id, owner_user_id AS ownerUserId,
                       remaining_amount - frozen_amount AS expirable
                FROM compute_card_hour_lot
                WHERE expires_at <= NOW() AND remaining_amount > frozen_amount
                  AND asset_type='STANDARD'
                  AND source_type NOT IN ('GPU_DEPOSIT','CARD_MARKET_SALE','CARD_MARKET_TRANSFER',
                      'SPECIFIC_EXPIRY_CONVERSION','STANDARD_ROLLOVER')
                ORDER BY id LIMIT 200 FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> lot : lots) {
            BigDecimal amount = decimal(lot.get("expirable"), 3);
            if (amount.compareTo(ZERO) <= 0) continue;
            long userId = longValue(lot.get("ownerUserId"));
            ensureAccount(userId);
            jdbc.update("UPDATE compute_card_hour_lot SET remaining_amount=frozen_amount WHERE id=?", lot.get("id"));
            jdbc.update("""
                    UPDATE compute_account SET available_card_hours=available_card_hours-?,
                        lifetime_consumption=lifetime_consumption+?, version=version+1 WHERE user_id=?
                    """, amount, amount, userId);
            ledger(userId, "EXPIRE", "DEBIT", amount, "LOT", Objects.toString(lot.get("id")),
                    "测试卡时到期", null, "expire-lot:" + lot.get("id"));
            notifyUser(userId, "CARD_HOUR_EXPIRED", "测试卡时已到期", amount + " 卡时已到期",
                    "LOT", Objects.toString(lot.get("id")));
        }
    }

    // ---------------------------------------------------------------------
    // Ledger primitives
    // ---------------------------------------------------------------------

    void ensureAccount(Long userId) {
        jdbc.update("INSERT IGNORE INTO compute_account(user_id) VALUES (?)", userId);
    }

    void credit(long userId, BigDecimal rawAmount, String sourceType, String sourceRef,
                        LocalDateTime expiresAt, String description, Long operatorUserId, String idempotencyKey) {
        BigDecimal amount = scale3(rawAmount);
        ensureAccount(userId);
        if (idempotencyKey != null && ledgerExists(idempotencyKey)) return;
        jdbc.update("""
                INSERT INTO compute_card_hour_lot(
                    owner_user_id, source_type, source_ref, original_amount,
                    remaining_amount, frozen_amount, expires_at)
                VALUES (?, ?, ?, ?, ?, 0.000, ?)
                """, userId, sourceType, safe(sourceRef, 128), amount, amount, timestamp(expiresAt));
        jdbc.update("""
                UPDATE compute_account SET available_card_hours=available_card_hours+?,
                    lifetime_income=lifetime_income+?, version=version+1 WHERE user_id=?
                """, amount, sourceType.endsWith("_INCOME") ? amount : ZERO, userId);
        ledger(userId, sourceType, "CREDIT", amount, sourceType, safe(sourceRef, 128), description,
                operatorUserId, idempotencyKey);
    }

    BigDecimal consumeAvailable(long userId, BigDecimal rawAmount, boolean allowPartial,
                                        String entryType, String referenceType, String referenceId,
                                        String description, Long operatorUserId, String idempotencyKey) {
        BigDecimal requested = scale3(rawAmount);
        ensureAccount(userId);
        if (idempotencyKey != null && ledgerExists(idempotencyKey)) return requested;
        Map<String, Object> account = lockAccount(userId);
        BigDecimal available = decimal(account.get("availableCardHours"), 3);
        BigDecimal target = allowPartial ? requested.min(available.max(ZERO)) : requested;
        if (!allowPartial && available.compareTo(requested) < 0) {
            throw new BizException(400, "可用卡时不足");
        }
        if (target.compareTo(ZERO) <= 0) return ZERO;

        BigDecimal remaining = target;
        List<Map<String, Object>> lots = jdbc.queryForList("""
                SELECT id, remaining_amount AS remainingAmount, frozen_amount AS frozenAmount
                FROM compute_card_hour_lot
                WHERE owner_user_id=? AND remaining_amount>frozen_amount
                  AND asset_type='STANDARD' AND custody_status='ACTIVE'
                  AND (expires_at IS NULL OR expires_at>NOW())
                ORDER BY CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END, expires_at, id
                FOR UPDATE
                """, userId);
        for (Map<String, Object> lot : lots) {
            BigDecimal lotAvailable = decimal(lot.get("remainingAmount"), 3)
                    .subtract(decimal(lot.get("frozenAmount"), 3));
            BigDecimal take = lotAvailable.min(remaining);
            if (take.compareTo(ZERO) <= 0) continue;
            jdbc.update("UPDATE compute_card_hour_lot SET remaining_amount=remaining_amount-? WHERE id=?",
                    take, lot.get("id"));
            remaining = remaining.subtract(take);
            if (remaining.compareTo(ZERO) <= 0) break;
        }
        BigDecimal consumed = target.subtract(remaining).setScale(3, RoundingMode.HALF_UP);
        if (!allowPartial && consumed.compareTo(target) != 0) {
            throw new BizException(409, "卡时批次与账户汇总不一致，请联系管理员");
        }
        if (consumed.compareTo(ZERO) > 0) {
            jdbc.update("""
                    UPDATE compute_account SET available_card_hours=available_card_hours-?,
                        lifetime_consumption=lifetime_consumption+?, version=version+1 WHERE user_id=?
                    """, consumed, consumed, userId);
            ledger(userId, entryType, "DEBIT", consumed, referenceType, referenceId, description,
                    operatorUserId, idempotencyKey);
        }
        return consumed;
    }

    private void debitAvailableWithoutConsumption(long userId, BigDecimal rawAmount,
                                                    String entryType, String referenceType, String referenceId,
                                                    String description, Long operatorUserId, String idempotencyKey) {
        BigDecimal amount = scale3(rawAmount);
        ensureAccount(userId);
        if (idempotencyKey != null && ledgerExists(idempotencyKey)) return;
        Map<String, Object> account = lockAccount(userId);
        if (decimal(account.get("availableCardHours"), 3).compareTo(amount) < 0) {
            throw new BizException(400, "可用卡时不足");
        }
        BigDecimal remaining = amount;
        List<Map<String, Object>> lots = jdbc.queryForList("""
                SELECT id, remaining_amount AS remainingAmount, frozen_amount AS frozenAmount
                FROM compute_card_hour_lot
                WHERE owner_user_id=? AND remaining_amount>frozen_amount
                  AND asset_type='STANDARD' AND custody_status='ACTIVE'
                  AND (expires_at IS NULL OR expires_at>NOW())
                ORDER BY CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END, expires_at, id
                FOR UPDATE
                """, userId);
        for (Map<String, Object> lot : lots) {
            BigDecimal lotAvailable = decimal(lot.get("remainingAmount"), 3)
                    .subtract(decimal(lot.get("frozenAmount"), 3));
            BigDecimal take = lotAvailable.min(remaining);
            if (take.compareTo(ZERO) <= 0) continue;
            jdbc.update("UPDATE compute_card_hour_lot SET remaining_amount=remaining_amount-? WHERE id=?",
                    take, lot.get("id"));
            remaining = remaining.subtract(take);
            if (remaining.compareTo(ZERO) <= 0) break;
        }
        if (remaining.compareTo(ZERO) > 0) {
            throw new BizException(409, "卡时批次与账户汇总不一致，请联系管理员");
        }
        jdbc.update("""
                UPDATE compute_account SET available_card_hours=available_card_hours-?,
                    version=version+1 WHERE user_id=?
                """, amount, userId);
        ledger(userId, entryType, "DEBIT", amount, referenceType, referenceId, description,
                operatorUserId, idempotencyKey);
    }

    private void freezeLots(long userId, BigDecimal rawAmount, LocalDateTime validUntil,
                            String purposeType, Long purposeId, String description) {
        BigDecimal amount = scale3(rawAmount);
        ensureAccount(userId);
        Map<String, Object> account = lockAccount(userId);
        if (decimal(account.get("availableCardHours"), 3).compareTo(amount) < 0) {
            throw new BizException(400, "可用卡时不足");
        }
        BigDecimal remaining = amount;
        List<Map<String, Object>> lots = jdbc.queryForList("""
                SELECT id, remaining_amount AS remainingAmount, frozen_amount AS frozenAmount,
                       expires_at AS expiresAt
                FROM compute_card_hour_lot
                WHERE owner_user_id=? AND remaining_amount>frozen_amount
                  AND asset_type='STANDARD' AND custody_status='ACTIVE'
                  AND (expires_at IS NULL OR expires_at>?)
                ORDER BY CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END, expires_at, id
                FOR UPDATE
                """, userId, timestamp(validUntil));
        for (Map<String, Object> lot : lots) {
            BigDecimal available = decimal(lot.get("remainingAmount"), 3)
                    .subtract(decimal(lot.get("frozenAmount"), 3));
            BigDecimal take = available.min(remaining);
            if (take.compareTo(ZERO) <= 0) continue;
            jdbc.update("UPDATE compute_card_hour_lot SET frozen_amount=frozen_amount+? WHERE id=?", take, lot.get("id"));
            jdbc.update("""
                    INSERT INTO compute_freeze_allocation(purpose_type, purpose_id, lot_id, amount, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, purposeType, purposeId, lot.get("id"), take, lot.get("expiresAt"));
            remaining = remaining.subtract(take);
            if (remaining.compareTo(ZERO) <= 0) break;
        }
        if (remaining.compareTo(ZERO) > 0) {
            throw new BizException(400, "没有足够的、在业务结束前仍有效的卡时");
        }
        jdbc.update("""
                UPDATE compute_account SET available_card_hours=available_card_hours-?,
                    frozen_card_hours=frozen_card_hours+?, version=version+1 WHERE user_id=?
                """, amount, amount, userId);
        ledger(userId, "FREEZE", "DEBIT", amount, purposeType, purposeId.toString(), description,
                null, "freeze:" + purposeType + ":" + purposeId);
    }

    private void releaseFreeze(long userId, String purposeType, Long purposeId, String description) {
        lockAccount(userId);
        List<Map<String, Object>> allocations = lockAllocations(purposeType, purposeId);
        BigDecimal total = allocations.stream().map(row -> decimal(row.get("amount"), 3))
                .reduce(ZERO, BigDecimal::add);
        if (total.compareTo(ZERO) <= 0) return;
        for (Map<String, Object> allocation : allocations) {
            jdbc.update("UPDATE compute_card_hour_lot SET frozen_amount=frozen_amount-? WHERE id=?",
                    allocation.get("amount"), allocation.get("lotId"));
        }
        jdbc.update("DELETE FROM compute_freeze_allocation WHERE purpose_type=? AND purpose_id=?",
                purposeType, purposeId);
        jdbc.update("""
                UPDATE compute_account SET available_card_hours=available_card_hours+?,
                    frozen_card_hours=frozen_card_hours-?, version=version+1 WHERE user_id=?
                """, total, total, userId);
        ledger(userId, "UNFREEZE", "CREDIT", total, purposeType, purposeId.toString(), description,
                null, "unfreeze:" + purposeType + ":" + purposeId);
    }

    private void consumeFrozen(long userId, String purposeType, Long purposeId, String description) {
        lockAccount(userId);
        List<Map<String, Object>> allocations = lockAllocations(purposeType, purposeId);
        BigDecimal total = allocations.stream().map(row -> decimal(row.get("amount"), 3))
                .reduce(ZERO, BigDecimal::add);
        if (total.compareTo(ZERO) <= 0) throw new BizException(409, "冻结卡时不存在");
        for (Map<String, Object> allocation : allocations) {
            jdbc.update("""
                    UPDATE compute_card_hour_lot
                    SET remaining_amount=remaining_amount-?, frozen_amount=frozen_amount-? WHERE id=?
                    """, allocation.get("amount"), allocation.get("amount"), allocation.get("lotId"));
        }
        jdbc.update("DELETE FROM compute_freeze_allocation WHERE purpose_type=? AND purpose_id=?",
                purposeType, purposeId);
        jdbc.update("""
                UPDATE compute_account SET frozen_card_hours=frozen_card_hours-?,
                    lifetime_consumption=lifetime_consumption+?, version=version+1 WHERE user_id=?
                """, total, total, userId);
        ledger(userId, "CONSUME_FROZEN", "DEBIT", total, purposeType, purposeId.toString(), description,
                null, "consume-frozen:" + purposeType + ":" + purposeId);
    }

    private void resolveFrozenReservation(long userId, Long reservationId, BigDecimal rawSettlement,
                                          String description, Long operatorUserId) {
        Map<String, Object> account = lockAccount(userId);
        List<Map<String, Object>> allocations = lockAllocations(PURPOSE_RESERVATION, reservationId);
        BigDecimal total = allocations.stream().map(row -> decimal(row.get("amount"), 3))
                .reduce(ZERO, BigDecimal::add);
        if (total.compareTo(ZERO) <= 0) throw new BizException(409, "订单冻结卡时不存在");
        BigDecimal settlement = scale3(rawSettlement);
        if (settlement.compareTo(ZERO) < 0 || settlement.compareTo(total) > 0) {
            throw new BizException(400, "结算卡时不能超过冻结卡时");
        }
        BigDecimal remainingSettlement = settlement;
        for (Map<String, Object> allocation : allocations) {
            BigDecimal allocated = decimal(allocation.get("amount"), 3);
            BigDecimal consume = allocated.min(remainingSettlement);
            jdbc.update("""
                    UPDATE compute_card_hour_lot
                    SET remaining_amount=remaining_amount-?, frozen_amount=frozen_amount-? WHERE id=?
                    """, consume, allocated, allocation.get("lotId"));
            remainingSettlement = remainingSettlement.subtract(consume);
        }
        if (remainingSettlement.compareTo(ZERO) > 0) {
            throw new BizException(409, "冻结批次不足，订单无法结算");
        }
        jdbc.update("DELETE FROM compute_freeze_allocation WHERE purpose_type=? AND purpose_id=?",
                PURPOSE_RESERVATION, reservationId);
        BigDecimal refund = total.subtract(settlement).setScale(3, RoundingMode.HALF_UP);
        jdbc.update("""
                UPDATE compute_account SET available_card_hours=available_card_hours+?,
                    frozen_card_hours=frozen_card_hours-?, lifetime_consumption=lifetime_consumption+?,
                    version=version+1 WHERE user_id=?
                """, refund, total, settlement, userId);
        if (settlement.compareTo(ZERO) > 0) {
            ledger(userId, "GPU_SETTLEMENT", "DEBIT", settlement, PURPOSE_RESERVATION,
                    reservationId.toString(), description, operatorUserId,
                    "gpu-settlement:" + reservationId);
        }
        if (refund.compareTo(ZERO) > 0) {
            ledger(userId, "GPU_REFUND", "CREDIT", refund, PURPOSE_RESERVATION,
                    reservationId.toString(), "GPU 异常订单退回冻结卡时", operatorUserId,
                    "gpu-refund:" + reservationId);
        }
    }

    private void moveFrozenLots(long senderUserId, long recipientUserId, String purposeType,
                                Long purposeId, String description) {
        ensureAccount(recipientUserId);
        lockAccount(senderUserId);
        lockAccount(recipientUserId);
        List<Map<String, Object>> allocations = lockAllocations(purposeType, purposeId);
        BigDecimal total = allocations.stream().map(row -> decimal(row.get("amount"), 3))
                .reduce(ZERO, BigDecimal::add);
        if (total.compareTo(ZERO) <= 0) throw new BizException(409, "冻结卡时不存在");
        for (Map<String, Object> allocation : allocations) {
            BigDecimal amount = decimal(allocation.get("amount"), 3);
            jdbc.update("""
                    UPDATE compute_card_hour_lot
                    SET remaining_amount=remaining_amount-?, frozen_amount=frozen_amount-? WHERE id=?
                    """, amount, amount, allocation.get("lotId"));
            jdbc.update("""
                    INSERT INTO compute_card_hour_lot(
                        owner_user_id, source_type, source_ref, original_amount,
                        remaining_amount, frozen_amount, expires_at)
                    VALUES (?, 'TRANSFER_IN', ?, ?, ?, 0.000, ?)
                    """, recipientUserId, purposeId.toString(), amount, amount, allocation.get("expiresAt"));
        }
        jdbc.update("DELETE FROM compute_freeze_allocation WHERE purpose_type=? AND purpose_id=?",
                purposeType, purposeId);
        jdbc.update("""
                UPDATE compute_account SET frozen_card_hours=frozen_card_hours-?,
                    lifetime_consumption=lifetime_consumption+?, version=version+1 WHERE user_id=?
                """, total, total, senderUserId);
        jdbc.update("""
                UPDATE compute_account SET available_card_hours=available_card_hours+?,
                    lifetime_income=lifetime_income+?, version=version+1 WHERE user_id=?
                """, total, total, recipientUserId);
        ledger(senderUserId, "TRANSFER_OUT", "DEBIT", total, purposeType, purposeId.toString(), description,
                null, "transfer-out:" + purposeId);
        ledger(recipientUserId, "TRANSFER_IN", "CREDIT", total, purposeType, purposeId.toString(), description,
                null, "transfer-in:" + purposeId);
    }

    private List<Map<String, Object>> lockAllocations(String purposeType, Long purposeId) {
        return jdbc.queryForList("""
                SELECT a.id, a.lot_id AS lotId, a.amount, a.expires_at AS expiresAt
                FROM compute_freeze_allocation a
                JOIN compute_card_hour_lot l ON l.id=a.lot_id
                WHERE a.purpose_type=? AND a.purpose_id=? ORDER BY a.id FOR UPDATE
                """, purposeType, purposeId);
    }

    private void ledger(long userId, String entryType, String direction, BigDecimal amount,
                        String referenceType, String referenceId, String description,
                        Long operatorUserId, String idempotencyKey) {
        Map<String, Object> account = lockAccount(userId);
        jdbc.update("""
                INSERT INTO compute_ledger(
                    user_id, entry_type, direction, amount, available_after, frozen_after,
                    reference_type, reference_id, description, operator_user_id, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, entryType, direction, scale3(amount), account.get("availableCardHours"),
                account.get("frozenCardHours"), safe(referenceType, 32), safe(referenceId, 128),
                safe(description, 512), operatorUserId, idempotencyKey);
    }

    private boolean ledgerExists(String idempotencyKey) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM compute_ledger WHERE idempotency_key=?",
                Long.class, idempotencyKey);
        return count != null && count > 0;
    }

    public boolean isSchemaAvailable() {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM compute_setting", Long.class);
            return true;
        } catch (DataAccessException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof SQLException sqlException
                        && (sqlException.getErrorCode() == 1146 || "42S02".equals(sqlException.getSQLState()))) {
                    // 迁移脚本未执行时保持存量人民币扣费路径，绝不阻断旧功能。
                    log.debug("算力中心表尚未创建，API 用量继续旧扣费路径");
                    return false;
                }
                cause = cause.getCause();
            }
            throw e;
        }
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    public boolean isAdmin(Long userId) {
        try {
            String email = jdbc.queryForObject("SELECT email FROM sys_user WHERE id=?", String.class, userId);
            return properties.isAdminEmail(email);
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) throw new BizException(403, "需要算力中心管理员权限");
    }

    void notifyUser(long userId, String type, String title, String content,
                            String referenceType, String referenceId) {
        jdbc.update("""
                INSERT INTO compute_notification(
                    user_id, notification_type, title, content, reference_type, reference_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, safe(type, 32), safe(title, 256), safe(content, 1000),
                safe(referenceType, 32), safe(referenceId, 128));
    }

    private void notifyAdmins(String type, String title, String content, String referenceType, String referenceId) {
        if (properties.getAdminEmails().isEmpty()) return;
        for (String email : properties.getAdminEmails()) {
            List<Long> users = jdbc.query("SELECT id FROM sys_user WHERE LOWER(email)=?", (rs, rowNum) -> rs.getLong(1), email);
            users.forEach(userId -> notifyUser(userId, type, title, content, referenceType, referenceId));
        }
    }

    private void audit(long operatorUserId, String action, String targetType, String targetId, String detail) {
        jdbc.update("""
                INSERT INTO compute_audit_log(operator_user_id, action, target_type, target_id, detail_json)
                VALUES (?, ?, ?, ?, JSON_OBJECT('detail', ?))
                """, operatorUserId, action, targetType, targetId, detail == null ? "" : detail);
    }

    private Map<String, Object> lockUser(long userId) {
        return queryOne("SELECT id, email, balance, station_id AS stationId FROM sys_user WHERE id=? FOR UPDATE", userId);
    }

    private Map<String, Object> findUserByEmail(String email) {
        requireText(email, "接收方邮箱不能为空");
        return queryOne("""
                SELECT id, email, station_id AS stationId FROM sys_user WHERE LOWER(email)=LOWER(?)
                """, email.trim());
    }

    private Map<String, Object> lockAccount(long userId) {
        ensureAccount(userId);
        return queryOne("""
                SELECT user_id AS userId, available_card_hours AS availableCardHours,
                       frozen_card_hours AS frozenCardHours
                FROM compute_account WHERE user_id=? FOR UPDATE
                """, userId);
    }

    private Map<String, Object> lockReservation(Long reservationId) {
        return queryOne("""
                SELECT id, order_id AS orderId, product_id AS productId, buyer_user_id AS buyerUserId,
                       supplier_user_id AS supplierUserId, start_time AS startTime,
                       end_time AS endTime, frozen_card_hours AS frozenCardHours, status,
                       trade_mode AS tradeMode, delivery_deadline_at AS deliveryDeadlineAt,
                       auto_confirm_at AS autoConfirmAt
                FROM compute_reservation WHERE id=? FOR UPDATE
                """, reservationId);
    }

    private Map<String, Object> lockTransfer(Long transferId) {
        return queryOne("""
                SELECT id, transfer_no AS transferNo, sender_user_id AS senderUserId,
                       recipient_user_id AS recipientUserId, amount, message, status,
                       review_reason AS reviewReason, expires_at AS expiresAt,
                       create_time AS createTime
                FROM compute_transfer WHERE id=? FOR UPDATE
                """, transferId);
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        try {
            return jdbc.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(404, "记录不存在");
        }
    }

    BigDecimal settingDecimal(String key, BigDecimal fallback) {
        try {
            String value = jdbc.queryForObject("SELECT setting_value FROM compute_setting WHERE setting_key=?",
                    String.class, key);
            return value == null ? fallback : new BigDecimal(value);
        } catch (DataAccessException | NumberFormatException e) {
            return fallback;
        }
    }

    private void upsertSetting(String key, String value, Long userId) {
        jdbc.update("""
                INSERT INTO compute_setting(setting_key, setting_value, update_user_id)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),
                    update_user_id=VALUES(update_user_id), update_time=CURRENT_TIMESTAMP
                """, key, value, userId);
    }

    private Long scalar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private Long lastInsertId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void validateTransferScope(long senderId, long recipientId) {
        Map<String, Object> sender = queryOne("""
                SELECT u.station_id AS stationId, s.url AS stationUrl FROM sys_user u
                LEFT JOIN relay_station s ON s.id=u.station_id WHERE u.id=?
                """, senderId);
        Map<String, Object> recipient = queryOne("""
                SELECT u.station_id AS stationId, s.url AS stationUrl FROM sys_user u
                LEFT JOIN relay_station s ON s.id=u.station_id WHERE u.id=?
                """, recipientId);
        if (Objects.equals(sender.get("stationId"), recipient.get("stationId"))) return;
        if (isKaiStationUrl(Objects.toString(sender.get("stationUrl"), ""))
                && isKaiStationUrl(Objects.toString(recipient.get("stationUrl"), ""))) return;
        throw new BizException(403, "非 KAI 公司中转站用户只能在同一中转站内转让卡时");
    }

    private boolean isKaiStationUrl(String rawUrl) {
        try {
            String host = URI.create(rawUrl).getHost();
            if (host == null) return false;
            String normalized = host.toLowerCase(Locale.ROOT);
            return properties.getKaiStationDomains().stream()
                    .anyMatch(domain -> normalized.equals(domain) || normalized.endsWith("." + domain));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void validateGpuProduct(ProductInput input) {
        requireText(input.name(), "商品名称不能为空");
        requireText(input.gpuModel(), "GPU 型号不能为空");
        if (input.gpuMemoryGb() == null || input.gpuMemoryGb() <= 0
                || input.gpuCount() == null || input.gpuCount() <= 0
                || input.packagePriceCardHours() == null || input.packagePriceCardHours().compareTo(BigDecimal.ZERO) <= 0
                || input.packageDurationHours() == null || input.packageDurationHours() <= 0
                || input.deliveryDeadlineHours() == null || input.deliveryDeadlineHours() <= 0) {
            throw new BizException(400, "GPU 规格、固定套餐时长、交付时限和卡时价格必须大于 0");
        }
        if (input.packageDurationHours() > 24 * 365 || input.deliveryDeadlineHours() > 24 * 30) {
            throw new BizException(400, "固定套餐时长或交付时限超出允许范围");
        }
    }

    static String normalizeSshPublicKey(String raw) {
        String key = Objects.toString(raw, "").trim();
        if (key.length() < 40 || key.length() > 16_384 || key.contains("\n") || key.contains("\r")
                || !(key.startsWith("ssh-ed25519 ") || key.startsWith("ssh-rsa ")
                || key.startsWith("ecdsa-sha2-nistp256 "))) {
            throw new BizException(400, "请提交一行有效的 SSH 公钥，禁止上传私钥");
        }
        if (key.contains("PRIVATE KEY")) throw new BizException(400, "禁止上传 SSH 私钥");
        return key;
    }

    private static void validateProductImage(MultipartFile image) {
        try {
            String contentType = image == null ? "" : Objects.toString(image.getContentType(), "").toLowerCase(Locale.ROOT);
            if (image == null || image.isEmpty() || image.getSize() > MAX_PRODUCT_IMAGE_BYTES
                    || !(contentType.equals("image/jpeg") || contentType.equals("image/png"))
                    || ImageIO.read(new ByteArrayInputStream(image.getBytes())) == null) {
                throw new BizException(400, "商品图片必须是 8MB 以内的 JPG 或 PNG");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "商品图片读取失败");
        }
    }

    private static void requireMarketplaceBuyer(Long buyerUserId, Map<String, Object> reservation) {
        if (!MARKETPLACE_FIXED.equals(reservation.get("tradeMode"))) {
            throw new BizException(400, "历史预订不支持该操作");
        }
        if (!Objects.equals(longOrNull(reservation.get("buyerUserId")), buyerUserId)) {
            throw new BizException(403, "无权操作该订单");
        }
    }

    private void validateApiUpstream(Long stationId, Long keyId) {
        if (stationId == null || keyId == null) throw new BizException(400, "请选择零售站和 API Key");
        Long matches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relay_station_key WHERE id=? AND station_id=?", Long.class, keyId, stationId);
        if (matches == null || matches == 0) throw new BizException(400, "零售站与 API Key 不匹配");
    }

    private static BigDecimal normalizePurchaseAmount(BigDecimal raw) {
        if (raw == null || raw.compareTo(MIN_PURCHASE) < 0) {
            throw new BizException(400, "每次至少购买 0.1 卡时");
        }
        try {
            BigDecimal amount = raw.setScale(1, RoundingMode.UNNECESSARY);
            return amount.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BizException(400, "购买数量必须是 0.1 卡时的倍数");
        }
    }

    private static BigDecimal normalizeWithdrawalAmount(BigDecimal raw) {
        if (raw == null || raw.compareTo(MIN_WITHDRAWAL) < 0) {
            throw new BizException(400, "每次至少兑换 0.1 卡时");
        }
        try {
            BigDecimal amount = raw.setScale(1, RoundingMode.UNNECESSARY);
            return amount.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BizException(400, "兑换数量必须是 0.1 卡时的倍数");
        }
    }

    private static BigDecimal positiveScale3(BigDecimal raw, String message) {
        if (raw == null || raw.compareTo(BigDecimal.ZERO) <= 0) throw new BizException(400, message);
        return scale3(raw);
    }

    private static BigDecimal positiveScale(BigDecimal raw, int scale, String message) {
        if (raw == null || raw.compareTo(BigDecimal.ZERO) <= 0) throw new BizException(400, message);
        return raw.setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale3(BigDecimal value) {
        if (value == null) return ZERO;
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(Object value, int scale) {
        if (value == null) return BigDecimal.ZERO.setScale(scale, RoundingMode.UNNECESSARY);
        if (value instanceof BigDecimal bigDecimal) return bigDecimal.setScale(scale, RoundingMode.HALF_UP);
        return new BigDecimal(value.toString()).setScale(scale, RoundingMode.HALF_UP);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(Objects.toString(value));
    }

    private static Long longOrNull(Object value) {
        return value == null ? null : longValue(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(Objects.toString(value, "0"));
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new BizException(400, message);
    }

    private static String safe(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /** 前端与管理员创建商品共用的输入模型。 */
    public record ProductInput(
            String name,
            String description,
            String region,
            String modelId,
            BigDecimal promptRatePerMillion,
            BigDecimal completionRatePerMillion,
            String gpuModel,
            Integer gpuMemoryGb,
            Integer gpuCount,
            BigDecimal pricePerGpuHour,
            LocalDateTime availableFrom,
            LocalDateTime availableTo,
            String deliveryMode,
            String slaDescription,
            Long supplierUserId,
            Long nodeId,
            Long packagePromptTokens,
            Long packageCompletionTokens,
            BigDecimal packagePriceCardHours,
            Long upstreamStationId,
            Long upstreamKeyId,
            Integer packageDurationHours,
            Integer deliveryDeadlineHours) {
    }

    public record ProductImage(byte[] content, String mimeType) {
    }
}
