package com.kod.config;

import com.kod.common.BizException;
import com.kod.service.ComputeCenterService;
import com.kod.service.ComputePackageService;
import com.kod.service.ComputeTrustService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 明确开启时，仅执行一次的本机 MVP 流程种子。所有资源都标记为内测，不冒充真实 H100。 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class ComputeDemoDataInitializer implements ApplicationRunner {

    private static final String MARKER = "local_demo_seed_mvp_v3";

    private final ComputeCenterProperties properties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final ComputeTrustService trust;
    private final ComputeCenterService center;
    private final ComputePackageService packages;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isDemoSeedEnabled()) return;
        transactionTemplate.executeWithoutResult(status -> seed());
    }

    private void seed() {
        int claimed = jdbc.update("""
                INSERT IGNORE INTO compute_setting(setting_key, setting_value, description)
                VALUES (?, 'RUNNING', '本机 MVP 一次性种子；存在即禁止重复扣款')
                """, MARKER);
        if (claimed == 0) {
            normalizeDemoRows();
            log.info("本机算力中心 MVP 种子已存在，跳过重复执行");
            return;
        }
        long supplierId = userId(properties.getDemoSupplierEmail(), "供应方");
        long buyerId = userId(properties.getDemoBuyerEmail(), "购买方");
        long reviewerId = reviewerId(supplierId);

        BigDecimal cnyBefore = jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=?", BigDecimal.class, buyerId);
        if (cnyBefore == null || cnyBefore.compareTo(new BigDecimal("1.0020")) < 0) {
            throw new BizException(400, "内测购买方人民币余额不足 1.0020，未执行任何种子数据");
        }
        center.purchaseCardHours(buyerId, new BigDecimal("1.000"));

        Map<String, Object> identity = trust.submitTestIdentity(supplierId);
        trust.reviewIdentity(reviewerId, number(identity.get("id")), true, "");
        Map<String, Object> supplier = center.applySupplier(supplierId, "仅内测模拟 H100 供应方",
                properties.getDemoSupplierEmail(), "仅验证供应方入驻、验机、上架、购买和交付流程，不代表真实机器");
        center.reviewSupplier(reviewerId, number(supplier.get("id")), true, "");

        Map<String, Object> node = trust.createNode(supplierId, new ComputeTrustService.NodeInput(
                "仅内测 H100 流程节点", "内部测试区", "H100", 80, 1,
                "模拟 CPU 信息", 0, 0, "无真实网络，仅流程测试"));
        long nodeId = number(node.get("id"));
        trust.reviewNode(reviewerId, nodeId, true, "", "仅验证流程；未连接、未验收任何真实 H100");
        trust.updateNodeStatus(reviewerId, nodeId, "RUNNING", "内测模拟部署完成");

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> gpuProduct = center.createSupplierGpuProduct(supplierId,
                new ComputeCenterService.ProductInput(
                        "仅内测 H100 预订流程商品", "模拟商品：不代表真实 H100，不提供真实算力",
                        "内部测试区", null, null, null, "H100", 80, 1,
                        null, null, null,
                        "仅内测占位交付信息", "仅验证平台交易状态机，无真实 SLA", supplierId,
                        nodeId, null, null, new BigDecimal("0.100"), null, null, 1, 24));
        long gpuProductId = number(gpuProduct.get("id"));
        center.reviewProduct(reviewerId, gpuProductId, true, "");

        String modelId = demoModelId();
        Map<String, Object> upstream = jdbc.queryForMap("""
                SELECT s.id AS stationId, k.id AS keyId FROM relay_station s
                JOIN relay_station_key k ON k.station_id=s.id ORDER BY k.id LIMIT 1
                """);
        Map<String, Object> apiProduct = center.createAdminApiProduct(reviewerId,
                new ComputeCenterService.ProductInput(
                        "KOD 内测固定 Token 套餐", "内部端到端测试套餐；模型 ID 必须与中转站日志完全一致",
                        "KAI 公司中转站", modelId, null, null, null, null, null,
                        null, null, null, "KOD 中转站", "仅内测，永久有效", null,
                        null, 100_000L, 50_000L, new BigDecimal("0.100"),
                        number(upstream.get("stationId")), number(upstream.get("keyId")), null, null));
        jdbc.update("UPDATE compute_product SET is_test=1 WHERE id=?", apiProduct.get("id"));
        packages.purchase(buyerId, number(apiProduct.get("id")), false);

        Map<String, Object> reservation = center.createMarketplaceOrder(buyerId, gpuProductId,
                "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKODINTERNALDEMOONLY000000000000000000000 demo", false);
        center.deliverMarketplaceOrder(supplierId, number(reservation.get("id")),
                "127.0.0.1", 22, "test-only", now.plusDays(1), now.plusDays(1).plusHours(1),
                "仅内测占位交付，不代表真实服务器，请勿尝试连接");

        jdbc.update("UPDATE compute_setting SET setting_value='COMPLETED', update_user_id=?, update_time=NOW() WHERE setting_key=?",
                supplierId, MARKER);
        BigDecimal cnyAfter = jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=?", BigDecimal.class, buyerId);
        log.info("MVP 种子完成：buyer={}, CNY {} -> {}；已购买 1.000 卡时、0.100 卡时 Token 套餐并冻结 0.100 卡时 GPU 预订；model={}",
                properties.getDemoBuyerEmail(), cnyBefore, cnyAfter, modelId);
    }

    private void normalizeDemoRows() {
        jdbc.update("UPDATE compute_product SET is_test=1 WHERE name IN ('KOD 内测固定 Token 套餐','仅内测 H100 预订流程商品')");
    }

    private long userId(String email, String role) {
        if (email == null || email.isBlank()) throw new BizException(400, "未配置内测" + role + "邮箱");
        List<Long> ids = jdbc.queryForList("SELECT id FROM sys_user WHERE LOWER(email)=?", Long.class, email);
        if (ids.isEmpty()) throw new BizException(404, "内测" + role + "账号不存在：" + email);
        return ids.get(0);
    }

    private long reviewerId(long excludedUserId) {
        for (String email : properties.getAdminEmails()) {
            List<Long> ids = jdbc.queryForList("SELECT id FROM sys_user WHERE LOWER(email)=? AND id<>?",
                    Long.class, email, excludedUserId);
            if (!ids.isEmpty()) return ids.get(0);
        }
        throw new BizException(409, "内测流程需要另一名管理员完成审核");
    }

    private String demoModelId() {
        try {
            List<String> models = jdbc.queryForList("""
                    SELECT model_name FROM api_request_logs
                    WHERE model_name IS NOT NULL AND model_name<>''
                    ORDER BY id DESC LIMIT 1
                    """, String.class);
            if (!models.isEmpty()) return models.get(0);
        } catch (Exception ignored) {
            // 新库可能还没有历史日志；使用明确的占位模型名。
        }
        return "KOD-INTERNAL-DEMO-MODEL";
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}
