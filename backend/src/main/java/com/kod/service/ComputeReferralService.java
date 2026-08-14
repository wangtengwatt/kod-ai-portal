package com.kod.service;

import com.kod.common.BizException;
import com.kod.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 算力中心独立邀请、首次充值返佣和七天延迟发放。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComputeReferralService {

    static final BigDecimal REWARD_RATE = new BigDecimal("0.05");
    static final BigDecimal REWARD_CAP = new BigDecimal("100.0000");
    private static final String WAITING = "WAITING";

    private final JdbcTemplate jdbc;

    @Transactional
    public Map<String, Object> profile(long userId) {
        String inviteCode = ensureProfile(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("inviteCode", inviteCode);
        result.put("inviteLink", "kod://compute/invite?code=" + inviteCode);
        result.put("rewardRate", REWARD_RATE);
        result.put("rewardCap", REWARD_CAP);
        result.put("invitedCount", scalarLong(
                "SELECT COUNT(*) FROM compute_referral_binding WHERE inviter_user_id=? AND status='ACTIVE'", userId));
        result.put("pendingCommission", scalarMoney("""
                SELECT COALESCE(SUM(reward_amount),0) FROM compute_referral_reward
                WHERE inviter_user_id=? AND status='WAITING'
                """, userId));
        result.put("paidCommission", scalarMoney("""
                SELECT COALESCE(SUM(reward_amount),0) FROM compute_referral_reward
                WHERE inviter_user_id=? AND status='PAID'
                """, userId));

        List<Map<String, Object>> bindings = jdbc.queryForList("""
                SELECT b.inviter_user_id AS inviterUserId, u.email AS inviterEmail, b.bound_at AS boundAt
                FROM compute_referral_binding b
                JOIN sys_user u ON u.id=b.inviter_user_id
                WHERE b.invitee_user_id=? AND b.status='ACTIVE'
                """, userId);
        if (!bindings.isEmpty()) {
            Map<String, Object> binding = bindings.get(0);
            result.put("bound", true);
            result.put("inviterEmail", maskEmail(Objects.toString(binding.get("inviterEmail"), "")));
            result.put("boundAt", binding.get("boundAt"));
            result.put("canBind", false);
            result.put("bindReason", "已绑定邀请人");
        } else {
            boolean hasRecharge = hasSuccessfulRecharge(userId);
            result.put("bound", false);
            result.put("canBind", !hasRecharge);
            result.put("bindReason", hasRecharge ? "已有成功充值记录，不能再绑定邀请人" : "");
        }
        return result;
    }

    public Map<String, Object> preview(long userId, String rawCode) {
        String code = normalizeInviteCode(rawCode);
        Map<String, Object> inviter = queryInviter(code);
        String reason = bindBlockReason(userId, ((Number) inviter.get("userId")).longValue());
        return Map.of(
                "inviteCode", code,
                "inviterEmail", maskEmail(Objects.toString(inviter.get("email"), "")),
                "canBind", reason.isBlank(),
                "reason", reason
        );
    }

    @Transactional
    public Map<String, Object> bind(long inviteeUserId, String rawCode, String rawDeviceId) {
        String code = normalizeInviteCode(rawCode);
        String deviceHash = hashDeviceId(rawDeviceId);
        jdbc.queryForObject("SELECT id FROM sys_user WHERE id=? FOR UPDATE", Long.class, inviteeUserId);
        Map<String, Object> inviter = queryInviter(code);
        long inviterUserId = ((Number) inviter.get("userId")).longValue();
        String reason = bindBlockReason(inviteeUserId, inviterUserId);
        if (!reason.isBlank()) throw new BizException(400, reason);
        Long deviceBindings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM compute_referral_binding WHERE device_hash=?", Long.class, deviceHash);
        if (deviceBindings != null && deviceBindings > 0) {
            throw new BizException(400, "该设备已经绑定过邀请关系，不能批量互邀");
        }
        try {
            jdbc.update("""
                    INSERT INTO compute_referral_binding(
                        inviter_user_id, invitee_user_id, invite_code, device_hash, status)
                    VALUES (?, ?, ?, ?, 'ACTIVE')
                    """, inviterUserId, inviteeUserId, code, deviceHash);
        } catch (DuplicateKeyException e) {
            throw new BizException(400, "该账号或设备已经绑定过邀请关系");
        }
        String inviteeEmail = jdbc.queryForObject(
                "SELECT email FROM sys_user WHERE id=?", String.class, inviteeUserId);
        notifyUser(inviteeUserId, "REFERRAL_BOUND", "邀请关系绑定成功",
                "已绑定邀请人 " + maskEmail(Objects.toString(inviter.get("email"), ""))
                        + "。首次充值成功并经过 7 天确认期后，邀请人可获得 5% 返佣。",
                "REFERRAL", code);
        notifyUser(inviterUserId, "REFERRAL_INVITEE_BOUND", "好友已接受邀请",
                maskEmail(inviteeEmail) + " 已接受你的邀请。其首次充值通过 7 天确认期后将发放返佣。",
                "REFERRAL", code);
        return profile(inviteeUserId);
    }

    public List<Map<String, Object>> rewards(long inviterUserId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.topup_order_no AS topupOrderNo, r.recharge_amount AS rechargeAmount,
                       r.reward_rate AS rewardRate, r.reward_cap AS rewardCap,
                       r.reward_amount AS rewardAmount, r.status, r.release_at AS releaseAt,
                       r.paid_at AS paidAt, r.cancel_reason AS cancelReason,
                       r.create_time AS createTime, u.email AS inviteeEmail
                FROM compute_referral_reward r
                JOIN sys_user u ON u.id=r.invitee_user_id
                WHERE r.inviter_user_id=? ORDER BY r.id DESC LIMIT 300
                """, inviterUserId);
        rows.forEach(row -> row.put("inviteeEmail", maskEmail(Objects.toString(row.get("inviteeEmail"), ""))));
        return rows;
    }

    /** 必须在充值订单状态和钱包入账成功的同一事务内调用。 */
    public void recordFirstSuccessfulRecharge(Order order) {
        try {
            doRecordFirstSuccessfulRecharge(order);
        } catch (DataAccessException e) {
            // 返佣是充值后的附加权益。迁移尚未完成时不得阻断用户钱包入账。
            log.error("记录首次充值返佣失败，充值钱包入账继续执行，orderId={}",
                    order == null ? null : order.getId(), e);
        }
    }

    private void doRecordFirstSuccessfulRecharge(Order order) {
        if (order == null || order.getId() == null || order.getUserId() == null
                || !"success".equals(order.getStatus())) return;
        List<Map<String, Object>> bindings = jdbc.queryForList("""
                SELECT id, inviter_user_id AS inviterUserId, invitee_user_id AS inviteeUserId
                FROM compute_referral_binding
                WHERE invitee_user_id=? AND status='ACTIVE' FOR UPDATE
                """, order.getUserId());
        if (bindings.isEmpty()) return;
        Long previousSuccess = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders
                WHERE user_id=? AND status='success' AND id<>?
                """, Long.class, order.getUserId(), order.getId());
        if (previousSuccess != null && previousSuccess > 0) return;
        Long existingReward = jdbc.queryForObject(
                "SELECT COUNT(*) FROM compute_referral_reward WHERE invitee_user_id=?",
                Long.class, order.getUserId());
        if (existingReward != null && existingReward > 0) return;

        Map<String, Object> binding = bindings.get(0);
        long inviterUserId = ((Number) binding.get("inviterUserId")).longValue();
        // 返佣按支付渠道实际收款计算，避免优惠、赠送额度被当成平台现金收入返佣。
        BigDecimal rechargeAmount = money(
                order.getActualPayment() != null ? order.getActualPayment() : order.getAmount());
        BigDecimal rewardAmount = calculateReward(rechargeAmount);
        if (rewardAmount.signum() <= 0) return;
        try {
            jdbc.update("""
                    INSERT INTO compute_referral_reward(
                        binding_id, inviter_user_id, invitee_user_id, topup_order_id, topup_order_no,
                        recharge_amount, reward_rate, reward_cap, reward_amount, status, release_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAITING', DATE_ADD(NOW(), INTERVAL 7 DAY))
                    """, binding.get("id"), inviterUserId, order.getUserId(), order.getId(), order.getOrderNo(),
                    rechargeAmount, REWARD_RATE, REWARD_CAP, rewardAmount);
        } catch (DuplicateKeyException e) {
            return;
        }
        notifyUser(inviterUserId, "REFERRAL_REWARD_WAITING", "邀请返佣进入确认期",
                "好友首次充值 ¥" + rechargeAmount.toPlainString() + "，预计返佣 ¥"
                        + rewardAmount.toPlainString() + "，将在 7 天确认期结束且订单未退款后发放。",
                "TOPUP", order.getOrderNo());
        notifyUser(order.getUserId(), "REFERRAL_REWARD_WAITING", "首次充值返佣进入确认期",
                "你的首次充值已经成功。经过 7 天确认期且订单未退款后，邀请人的返佣将自动到账。",
                "TOPUP", order.getOrderNo());
    }

    @Transactional
    public void releaseDueRewards() {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM compute_referral_reward
                WHERE status='WAITING' AND release_at<=NOW() ORDER BY id LIMIT 100
                """, Long.class);
        for (Long id : ids) releaseReward(id);
    }

    public Map<String, Object> summary(long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("commissionIncome", scalarMoney("""
                SELECT COALESCE(SUM(reward_amount),0) FROM compute_referral_reward
                WHERE inviter_user_id=? AND status='PAID'
                """, userId));
        result.put("pendingCommission", scalarMoney("""
                SELECT COALESCE(SUM(reward_amount),0) FROM compute_referral_reward
                WHERE inviter_user_id=? AND status='WAITING'
                """, userId));
        result.put("invitedCount", scalarLong(
                "SELECT COUNT(*) FROM compute_referral_binding WHERE inviter_user_id=? AND status='ACTIVE'", userId));
        return result;
    }

    private void releaseReward(long rewardId) {
        Map<String, Object> reward = jdbc.queryForMap("""
                SELECT id, inviter_user_id AS inviterUserId, invitee_user_id AS inviteeUserId,
                       topup_order_id AS topupOrderId,
                       topup_order_no AS topupOrderNo, reward_amount AS rewardAmount, status
                FROM compute_referral_reward WHERE id=? FOR UPDATE
                """, rewardId);
        if (!WAITING.equals(reward.get("status"))) return;
        String orderStatus = jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, reward.get("topupOrderId"));
        long inviterUserId = ((Number) reward.get("inviterUserId")).longValue();
        long inviteeUserId = ((Number) reward.get("inviteeUserId")).longValue();
        BigDecimal amount = money(reward.get("rewardAmount"));
        if (List.of("failed", "expired", "cancelled", "refunded").contains(
                Objects.toString(orderStatus, "").toLowerCase(Locale.ROOT))) {
            jdbc.update("""
                    UPDATE compute_referral_reward
                    SET status='CANCELLED',cancelled_at=NOW(),cancel_reason='首次充值订单已退款或失效'
                    WHERE id=?
                    """, rewardId);
            notifyUser(inviterUserId, "REFERRAL_REWARD_CANCELLED", "邀请返佣已取消",
                    "首次充值订单已退款或失效，本次待发放返佣已取消。",
                    "TOPUP", Objects.toString(reward.get("topupOrderNo"), ""));
            notifyUser(inviteeUserId, "REFERRAL_REWARD_CANCELLED", "首次充值返佣已取消",
                    "首次充值订单已退款或失效，关联的邀请返佣已取消。",
                    "TOPUP", Objects.toString(reward.get("topupOrderNo"), ""));
            return;
        }
        if (!"success".equalsIgnoreCase(orderStatus)) return;
        jdbc.update("UPDATE sys_user SET balance=COALESCE(balance,0)+? WHERE id=?", amount, inviterUserId);
        jdbc.update("UPDATE compute_referral_reward SET status='PAID',paid_at=NOW() WHERE id=?", rewardId);
        notifyUser(inviterUserId, "REFERRAL_REWARD_PAID", "邀请返佣已到账",
                "邀请返佣 ¥" + amount.toPlainString() + " 已进入 KOD 人民币钱包。",
                "TOPUP", Objects.toString(reward.get("topupOrderNo"), ""));
        notifyUser(inviteeUserId, "REFERRAL_REWARD_PAID", "首次充值返佣已发放",
                "你的首次充值已通过 7 天确认期，邀请人的返佣已经到账。",
                "TOPUP", Objects.toString(reward.get("topupOrderNo"), ""));
    }

    private String ensureProfile(long userId) {
        List<String> existing = jdbc.query(
                "SELECT invite_code FROM compute_referral_profile WHERE user_id=?",
                (rs, rowNum) -> rs.getString(1), userId);
        if (!existing.isEmpty()) return existing.get(0);
        for (int attempt = 0; attempt < 3; attempt++) {
            String code = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
            try {
                jdbc.update("INSERT INTO compute_referral_profile(user_id,invite_code) VALUES (?,?)", userId, code);
                return code;
            } catch (DuplicateKeyException e) {
                existing = jdbc.query(
                        "SELECT invite_code FROM compute_referral_profile WHERE user_id=?",
                        (rs, rowNum) -> rs.getString(1), userId);
                if (!existing.isEmpty()) return existing.get(0);
            }
        }
        throw new BizException(500, "生成邀请链接失败，请稍后重试");
    }

    private Map<String, Object> queryInviter(String inviteCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.user_id AS userId, u.email
                FROM compute_referral_profile p JOIN sys_user u ON u.id=p.user_id
                WHERE p.invite_code=?
                """, inviteCode);
        if (rows.isEmpty()) throw new BizException(404, "邀请链接无效或已失效");
        return rows.get(0);
    }

    private String bindBlockReason(long inviteeUserId, long inviterUserId) {
        if (inviteeUserId == inviterUserId) return "不能邀请自己";
        Long binding = jdbc.queryForObject(
                "SELECT COUNT(*) FROM compute_referral_binding WHERE invitee_user_id=?",
                Long.class, inviteeUserId);
        if (binding != null && binding > 0) return "该账号已经绑定过邀请人，不能更改";
        if (hasSuccessfulRecharge(inviteeUserId)) return "已有成功充值记录，不能再绑定邀请人";
        return "";
    }

    private boolean hasSuccessfulRecharge(long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id=? AND status='success'", Long.class, userId);
        return count != null && count > 0;
    }

    private void notifyUser(long userId, String type, String title, String content,
                            String referenceType, String referenceId) {
        jdbc.update("""
                INSERT INTO compute_notification(
                    user_id, notification_type, title, content, reference_type, reference_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, type, title, content, referenceType, referenceId);
    }

    static BigDecimal calculateReward(BigDecimal rawRechargeAmount) {
        return money(rawRechargeAmount).multiply(REWARD_RATE)
                .min(REWARD_CAP).setScale(4, RoundingMode.HALF_UP);
    }

    static String maskEmail(String email) {
        String value = Objects.toString(email, "").trim();
        int at = value.indexOf('@');
        if (at <= 0) return "***";
        String local = value.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + value.substring(at);
    }

    static String normalizeInviteCode(String raw) {
        String value = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[0-9a-f]{32}")) throw new BizException(400, "邀请链接格式不正确");
        return value;
    }

    static String hashDeviceId(String rawDeviceId) {
        String deviceId = Objects.toString(rawDeviceId, "").trim();
        if (deviceId.length() < 8 || deviceId.length() > 256) {
            throw new BizException(400, "无法识别当前设备，请重启 KOD 后重试");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    ("kod-compute-referral:" + deviceId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("设备标识哈希失败", e);
        }
    }

    private BigDecimal scalarMoney(String sql, Object... args) {
        return money(jdbc.queryForObject(sql, BigDecimal.class, args));
    }

    private long scalarLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static BigDecimal money(Object value) {
        if (value == null) return new BigDecimal("0.0000");
        return new BigDecimal(value.toString()).setScale(4, RoundingMode.HALF_UP);
    }
}
