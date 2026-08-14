package com.kod.service;

import com.kod.common.BizException;
import com.kod.config.ComputeCenterProperties;
import com.kod.util.ComputeDeliveryCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 实名认证、资源商资质和 GPU 资源证明审核流程。平台不登录或控制商家服务器。 */
@Service
@RequiredArgsConstructor
public class ComputeTrustService {

    private static final long MAX_DOCUMENT_BYTES = 8L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final ComputeCenterProperties properties;
    private final ComputeDeliveryCrypto crypto;
    private final ComputePrivateDocumentStorage storage;

    public Map<String, Object> identityMe(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, verification_type AS verificationType, identity_no_masked AS identityNoMasked,
                       status, rejection_reason AS rejectionReason, reviewed_at AS reviewedAt,
                       purge_after AS purgeAfter, create_time AS createTime
                FROM compute_identity_verification WHERE user_id=? ORDER BY id DESC LIMIT 1
                """, userId);
        return rows.isEmpty() ? Map.of("status", "NONE") : rows.get(0);
    }

    @Transactional
    public Map<String, Object> submitIdentity(Long userId, String realName, String identityNo,
                                               MultipartFile front, MultipartFile back) {
        if (realName == null || realName.trim().length() < 2 || realName.trim().length() > 64) {
            throw new BizException(400, "真实姓名长度必须为 2 至 64 个字符");
        }
        String normalizedNo = Objects.toString(identityNo, "").replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (!normalizedNo.matches("[0-9]{17}[0-9X]")) {
            throw new BizException(400, "身份证号格式不正确");
        }
        validateImage(front, "身份证正面");
        validateImage(back, "身份证反面");
        String fingerprint = crypto.fingerprint(normalizedNo);
        Long accounts = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT user_id) FROM compute_identity_verification
                WHERE identity_fingerprint=? AND user_id<>? AND status IN ('PENDING','APPROVED','TEST_APPROVED')
                """, Long.class, fingerprint, userId);
        if (accounts != null && accounts >= 4) {
            throw new BizException(409, "同一身份证最多可认证五个 KOD 账号");
        }
        Long active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_identity_verification
                WHERE user_id=? AND status IN ('PENDING','APPROVED','TEST_APPROVED')
                """, Long.class, userId);
        if (active != null && active > 0) throw new BizException(409, "当前已有待审核或已通过的实名认证");

        String frontId = null;
        String backId = null;
        try {
            frontId = storage.store(front.getBytes());
            backId = storage.store(back.getBytes());
            jdbc.update("""
                    INSERT INTO compute_identity_verification(
                        user_id, verification_type, real_name_ciphertext, identity_no_ciphertext,
                        identity_fingerprint, identity_no_masked, front_file_id, back_file_id, status)
                    VALUES (?, 'REAL', ?, ?, ?, ?, ?, ?, 'PENDING')
                    """, userId, crypto.encrypt(realName.trim()), crypto.encrypt(normalizedNo), fingerprint,
                    maskIdentity(normalizedNo), frontId, backId);
        } catch (Exception e) {
            storage.delete(frontId);
            storage.delete(backId);
            if (e instanceof BizException bizException) throw bizException;
            throw new BizException(500, "实名认证提交失败");
        }
        notifyAdmins("IDENTITY_REVIEW", "实名认证待审核", "用户 " + userEmail(userId) + " 提交了实名认证",
                "IDENTITY", Long.toString(lastInsertId()));
        return identityMe(userId);
    }

    @Transactional
    public Map<String, Object> submitTestIdentity(Long userId) {
        if (!properties.isTestIdentityEnabled()) throw new BizException(403, "当前环境未开启模拟实名认证");
        Long active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM compute_identity_verification
                WHERE user_id=? AND status IN ('PENDING','APPROVED','TEST_APPROVED')
                """, Long.class, userId);
        if (active != null && active > 0) return identityMe(userId);
        try {
            String identityNo = "TEST" + userId;
            String frontId = storage.store(testDocument("仅内测 · 非真实身份证 · 正面", userId));
            String backId = storage.store(testDocument("仅内测 · 非真实身份证 · 反面", userId));
            jdbc.update("""
                    INSERT INTO compute_identity_verification(
                        user_id, verification_type, real_name_ciphertext, identity_no_ciphertext,
                        identity_fingerprint, identity_no_masked, front_file_id, back_file_id, status)
                    VALUES (?, 'TEST', ?, ?, ?, '仅内测', ?, ?, 'PENDING')
                    """, userId, crypto.encrypt("仅内测模拟供应商"), crypto.encrypt(identityNo),
                    crypto.fingerprint(identityNo), frontId, backId);
            notifyAdmins("IDENTITY_REVIEW", "模拟实名认证待审核", userEmail(userId) + " 提交了内测模拟认证",
                    "IDENTITY", Long.toString(lastInsertId()));
            return identityMe(userId);
        } catch (Exception e) {
            if (e instanceof BizException bizException) throw bizException;
            throw new BizException(500, "模拟实名认证创建失败");
        }
    }

    public List<Map<String, Object>> adminIdentities(Long adminUserId) {
        requireAdmin(adminUserId);
        return jdbc.queryForList("""
                SELECT v.id, v.user_id AS userId, u.email, v.verification_type AS verificationType,
                       v.identity_no_masked AS identityNoMasked, v.status,
                       v.rejection_reason AS rejectionReason, v.create_time AS createTime
                FROM compute_identity_verification v JOIN sys_user u ON u.id=v.user_id
                ORDER BY v.id DESC LIMIT 200
                """);
    }

    public Map<String, Object> adminIdentityDetail(Long adminUserId, Long identityId) {
        requireAdmin(adminUserId);
        Map<String, Object> row = one("""
                SELECT v.id, v.user_id AS userId, u.email, v.verification_type AS verificationType,
                       v.real_name_ciphertext AS realNameCiphertext,
                       v.identity_no_ciphertext AS identityNoCiphertext,
                       v.identity_no_masked AS identityNoMasked, v.status,
                       v.rejection_reason AS rejectionReason, v.create_time AS createTime
                FROM compute_identity_verification v JOIN sys_user u ON u.id=v.user_id WHERE v.id=?
                """, identityId);
        row.put("realName", crypto.decrypt(Objects.toString(row.remove("realNameCiphertext"), "")));
        row.put("identityNo", crypto.decrypt(Objects.toString(row.remove("identityNoCiphertext"), "")));
        return row;
    }

    public byte[] adminIdentityDocument(Long adminUserId, Long identityId, String side) {
        requireAdmin(adminUserId);
        String column = "back".equalsIgnoreCase(side) ? "back_file_id" : "front_file_id";
        String fileId = jdbc.queryForObject("SELECT " + column + " FROM compute_identity_verification WHERE id=?",
                String.class, identityId);
        if (fileId == null || fileId.isBlank()) throw new BizException(404, "证件材料已清理");
        return storage.read(fileId);
    }

    public byte[] identityDocument(Long userId, String side) {
        String column = "back".equalsIgnoreCase(side) ? "back_file_id" : "front_file_id";
        List<String> files = jdbc.queryForList("SELECT " + column + " FROM compute_identity_verification "
                + "WHERE user_id=? ORDER BY id DESC LIMIT 1", String.class, userId);
        if (files.isEmpty() || files.get(0) == null || files.get(0).isBlank()) {
            throw new BizException(404, "证件材料已清理或不存在");
        }
        return storage.read(files.get(0));
    }

    @Transactional
    public Map<String, Object> reviewIdentity(Long adminUserId, Long identityId, boolean approved, String reason) {
        requireAdmin(adminUserId);
        Map<String, Object> row = one("""
                SELECT id, user_id AS userId, verification_type AS verificationType, status
                FROM compute_identity_verification WHERE id=? FOR UPDATE
                """, identityId);
        if (!"PENDING".equals(row.get("status"))) throw new BizException(400, "实名认证不在待审核状态");
        if (number(row.get("userId")) == adminUserId) {
            throw new BizException(403, "实名认证不得由本人审核，请使用另一名管理员账号");
        }
        if (!approved && (reason == null || reason.isBlank())) throw new BizException(400, "拒绝时必须填写原因");
        String status = approved
                ? ("TEST".equals(row.get("verificationType")) ? "TEST_APPROVED" : "APPROVED")
                : "REJECTED";
        jdbc.update("""
                UPDATE compute_identity_verification SET status=?, rejection_reason=?, reviewed_by=?,
                    reviewed_at=NOW(), closed_at=CASE WHEN ?='REJECTED' THEN NOW() ELSE NULL END,
                    purge_after=CASE WHEN ?='REJECTED' THEN DATE_ADD(NOW(), INTERVAL 30 DAY) ELSE NULL END
                WHERE id=?
                """, status, approved ? "" : safe(reason, 512), adminUserId, status, status, identityId);
        long userId = number(row.get("userId"));
        notifyUser(userId, "IDENTITY_REVIEWED", approved ? "实名认证已通过" : "实名认证未通过",
                approved ? ("TEST_APPROVED".equals(status) ? "仅内测模拟认证已通过，只能发布测试商品" : "实名认证已通过")
                        : safe(reason, 512), "IDENTITY", identityId.toString());
        return identityMe(userId);
    }

    @Transactional
    public void closeIdentity(Long userId) {
        Map<String, Object> identity = one("""
                SELECT id FROM compute_identity_verification
                WHERE user_id=? AND status IN ('APPROVED','TEST_APPROVED') ORDER BY id DESC LIMIT 1 FOR UPDATE
                """, userId);
        jdbc.update("""
                UPDATE compute_identity_verification SET status='REVOKED', closed_at=NOW(),
                    purge_after=DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE id=?
                """, identity.get("id"));
        jdbc.update("UPDATE compute_supplier SET status='SUSPENDED' WHERE user_id=?", userId);
        jdbc.update("UPDATE compute_gpu_node SET status='OFFLINE' WHERE supplier_user_id=?", userId);
        jdbc.update("UPDATE compute_product SET status='OFFLINE' WHERE supplier_user_id=?", userId);
    }

    public Map<String, Object> approvedIdentity(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, verification_type AS verificationType, status
                FROM compute_identity_verification
                WHERE user_id=? AND status IN ('APPROVED','TEST_APPROVED') ORDER BY id DESC LIMIT 1
                """, userId);
        if (rows.isEmpty()) throw new BizException(403, "请先完成实名认证");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> createNode(Long userId, NodeInput input) {
        return createNode(userId, input, null);
    }

    @Transactional
    public Map<String, Object> createNode(Long userId, NodeInput input, MultipartFile resourceProof) {
        Map<String, Object> identity = approvedIdentity(userId);
        Long supplier = jdbc.queryForObject("SELECT COUNT(*) FROM compute_supplier WHERE user_id=? AND status='APPROVED'",
                Long.class, userId);
        if (supplier == null || supplier == 0) throw new BizException(403, "供应方入驻尚未通过审核");
        validateNode(input);
        boolean test = "TEST".equals(identity.get("verificationType"));
        byte[] proofBytes;
        String proofMimeType;
        try {
            if (resourceProof == null || resourceProof.isEmpty()) {
                if (!test) throw new BizException(400, "请上传 GPU 资源证明图片");
                proofBytes = testDocument("仅内测 GPU 资源证明", userId);
                proofMimeType = MediaTypes.PNG;
            } else {
                validateImage(resourceProof, "GPU 资源证明");
                proofBytes = resourceProof.getBytes();
                proofMimeType = normalizedImageMimeType(resourceProof);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "GPU 资源证明读取失败");
        }
        String proofFileId = storage.store(proofBytes);
        jdbc.update("""
                INSERT INTO compute_gpu_node(
                    supplier_user_id, identity_verification_id, node_name, region,
                    gpu_model, gpu_memory_gb, gpu_count, cpu_description, ram_gb, storage_gb,
                    network_description, proof_file_id, proof_mime_type,
                    ssh_host_ciphertext, ssh_port, ssh_username_ciphertext,
                    ssh_auth_type, ssh_credential_ciphertext, status, is_test)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', 22, '', 'NONE', '', 'PENDING', ?)
                """, userId, identity.get("id"), input.nodeName().trim(), safe(input.region(), 128),
                input.gpuModel().trim(), input.gpuMemoryGb(), input.gpuCount(), safe(input.cpuDescription(), 256),
                Math.max(0, input.ramGb()), Math.max(0, input.storageGb()), safe(input.networkDescription(), 256),
                proofFileId, proofMimeType, test ? 1 : 0);
        Long id = lastInsertId();
        notifyAdmins("GPU_NODE_REVIEW", "GPU 资源资质待审核", input.nodeName() + " 已提交资源证明", "GPU_NODE", id.toString());
        return nodeDetail(userId, id, false);
    }

    public List<Map<String, Object>> myNodes(Long userId) {
        return jdbc.queryForList(nodeSelect() + " WHERE n.supplier_user_id=? ORDER BY n.id DESC", userId);
    }

    public List<Map<String, Object>> adminNodes(Long adminUserId) {
        requireAdmin(adminUserId);
        return jdbc.queryForList(nodeSelect() + " ORDER BY n.id DESC LIMIT 200");
    }

    public byte[] nodeProof(Long adminUserId, Long nodeId) {
        requireAdmin(adminUserId);
        String fileId = jdbc.queryForObject("SELECT proof_file_id FROM compute_gpu_node WHERE id=?", String.class, nodeId);
        if (fileId == null || fileId.isBlank()) throw new BizException(404, "该历史资源未上传证明图片");
        return storage.read(fileId);
    }

    @Transactional
    public Map<String, Object> reviewNode(Long adminUserId, Long nodeId, boolean approved,
                                           String reason, String verificationNote) {
        requireAdmin(adminUserId);
        Map<String, Object> row = one("SELECT id, supplier_user_id AS supplierUserId, status FROM compute_gpu_node WHERE id=? FOR UPDATE",
                nodeId);
        if (!"PENDING".equals(row.get("status"))) throw new BizException(400, "节点不在待验机状态");
        if (number(row.get("supplierUserId")) == adminUserId) {
            throw new BizException(403, "设备不得由供应方本人审核，请使用另一名管理员账号");
        }
        if (!approved && (reason == null || reason.isBlank())) throw new BizException(400, "拒绝时必须填写原因");
        jdbc.update("""
                UPDATE compute_gpu_node SET status=?, review_reason=?, verification_note=?,
                    reviewed_by=?, reviewed_at=NOW() WHERE id=?
                """, approved ? "RUNNING" : "REJECTED", approved ? "" : safe(reason, 512),
                safe(verificationNote, 1000), adminUserId, nodeId);
        notifyUser(number(row.get("supplierUserId")), "GPU_NODE_REVIEWED", approved ? "GPU 节点验机通过" : "GPU 节点验机未通过",
                approved ? "资源证明审核通过，现在可以发布固定 GPU 套餐" : safe(reason, 512), "GPU_NODE", nodeId.toString());
        return nodeDetail(adminUserId, nodeId, true);
    }

    public Map<String, Object> requireRunningNode(Long supplierUserId, Long nodeId) {
        if (nodeId == null) throw new BizException(400, "必须选择运行中的 GPU 节点");
        Map<String, Object> node = one("""
                SELECT id, supplier_user_id AS supplierUserId, gpu_model AS gpuModel,
                       gpu_memory_gb AS gpuMemoryGb, gpu_count AS gpuCount, region, status, is_test AS isTest
                FROM compute_gpu_node WHERE id=?
                """, nodeId);
        if (number(node.get("supplierUserId")) != supplierUserId || !"RUNNING".equals(node.get("status"))) {
            throw new BizException(403, "只能基于本人运行中的 GPU 节点发布商品");
        }
        return node;
    }

    @Transactional
    public Map<String, Object> updateNodeStatus(Long adminUserId, Long nodeId, String rawStatus, String reason) {
        requireAdmin(adminUserId);
        String target = Objects.toString(rawStatus, "").trim().toUpperCase(Locale.ROOT);
        if (!List.of("DEPLOYING", "RUNNING", "PENDING_ACTION", "OFFLINE").contains(target)) {
            throw new BizException(400, "设备状态只能切换为部署中、运行中、待处理或已离线");
        }
        Map<String, Object> node = one("""
                SELECT id, supplier_user_id AS supplierUserId, node_name AS nodeName, status
                FROM compute_gpu_node WHERE id=? FOR UPDATE
                """, nodeId);
        String current = Objects.toString(node.get("status"), "");
        if (List.of("PENDING", "REJECTED").contains(current)) {
            throw new BizException(400, "设备必须先完成验机审核");
        }
        if (target.equals(current)) return nodeDetail(adminUserId, nodeId, true);
        if (List.of("PENDING_ACTION", "OFFLINE").contains(target)
                && Objects.toString(reason, "").isBlank()) {
            throw new BizException(400, "切换为待处理或已离线时必须填写原因");
        }
        jdbc.update("""
                UPDATE compute_gpu_node SET status=?, review_reason=?, update_time=NOW() WHERE id=?
                """, target, List.of("PENDING_ACTION", "OFFLINE").contains(target) ? safe(reason, 512) : "", nodeId);

        if (!"RUNNING".equals(target)) {
            jdbc.update("UPDATE compute_product SET status='PAUSED' WHERE node_id=? AND status='PUBLISHED'", nodeId);
            List<Map<String, Object>> affected = jdbc.queryForList("""
                    SELECT r.id, r.buyer_user_id AS buyerUserId
                    FROM compute_reservation r JOIN compute_product p ON p.id=r.product_id
                    WHERE p.node_id=? AND r.trade_mode='LEGACY_RESERVATION'
                      AND r.status IN ('PENDING_DELIVERY','CONFIRMED','IN_USE')
                    FOR UPDATE
                    """, nodeId);
            String incident = Objects.toString(reason, "").isBlank()
                    ? (target.equals("DEPLOYING") ? "设备重新部署" : "设备状态异常")
                    : safe(reason, 512);
            jdbc.update("""
                    UPDATE compute_reservation r JOIN compute_product p ON p.id=r.product_id
                    SET r.status_before_incident=r.status, r.status='EXCEPTION_PENDING', r.incident_reason=?
                    WHERE p.node_id=? AND r.trade_mode='LEGACY_RESERVATION'
                      AND r.status IN ('PENDING_DELIVERY','CONFIRMED','IN_USE')
                    """, incident, nodeId);
            jdbc.update("""
                    UPDATE compute_order o JOIN compute_reservation r ON r.order_id=o.id
                    JOIN compute_product p ON p.id=r.product_id
                    SET o.status='EXCEPTION_PENDING'
                    WHERE p.node_id=? AND r.status='EXCEPTION_PENDING'
                    """, nodeId);
            for (Map<String, Object> item : affected) {
                notifyUser(number(item.get("buyerUserId")), "GPU_RESERVATION_EXCEPTION", "GPU 订单异常待处理",
                        incident + "；订单已暂停自动结算，等待管理员退款或结算", "RESERVATION", item.get("id").toString());
            }
        }
        notifyUser(number(node.get("supplierUserId")), "GPU_NODE_STATUS_CHANGED", "设备状态已更新",
                node.get("nodeName") + "：" + target + (Objects.toString(reason, "").isBlank() ? "" : "；" + safe(reason, 512)),
                "GPU_NODE", nodeId.toString());
        return nodeDetail(adminUserId, nodeId, true);
    }

    @Transactional
    public void purgeExpiredIdentityDocuments() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, front_file_id AS frontFileId, back_file_id AS backFileId
                FROM compute_identity_verification
                WHERE purge_after<=NOW() AND purged_at IS NULL LIMIT 100 FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> row : rows) {
            storage.delete(Objects.toString(row.get("frontFileId"), ""));
            storage.delete(Objects.toString(row.get("backFileId"), ""));
            jdbc.update("""
                    UPDATE compute_identity_verification SET real_name_ciphertext=NULL,
                        identity_no_ciphertext=NULL, front_file_id=NULL, back_file_id=NULL, purged_at=NOW()
                    WHERE id=?
                    """, row.get("id"));
        }
    }

    private Map<String, Object> nodeDetail(Long viewerUserId, Long nodeId, boolean admin) {
        Map<String, Object> row = one(nodeSelect() + " WHERE n.id=?", nodeId);
        if (!admin && number(row.get("supplierUserId")) != viewerUserId) throw new BizException(403, "无权查看该节点");
        return row;
    }

    private String nodeSelect() {
        return """
                SELECT n.id, n.supplier_user_id AS supplierUserId, u.email,
                       n.node_name AS nodeName, n.region, n.gpu_model AS gpuModel,
                       n.gpu_memory_gb AS gpuMemoryGb, n.gpu_count AS gpuCount,
                       n.cpu_description AS cpuDescription, n.ram_gb AS ramGb,
                        n.storage_gb AS storageGb, n.network_description AS networkDescription,
                       n.status, n.is_test AS isTest, n.review_reason AS reviewReason,
                       n.verification_note AS verificationNote, n.create_time AS createTime
                FROM compute_gpu_node n JOIN sys_user u ON u.id=n.supplier_user_id
                """;
    }

    private void validateNode(NodeInput input) {
        if (input == null || input.nodeName() == null || input.nodeName().isBlank()
                || input.gpuModel() == null || input.gpuModel().isBlank()
                || input.gpuMemoryGb() <= 0 || input.gpuCount() <= 0) {
            throw new BizException(400, "GPU 资源信息不完整");
        }
    }

    private String normalizedImageMimeType(MultipartFile file) {
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        return contentType.contains("png") ? MediaTypes.PNG : MediaTypes.JPEG;
    }

    private void validateImage(MultipartFile file, String label) {
        try {
            String contentType = file == null ? "" : Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
            if (file == null || file.isEmpty() || file.getSize() > MAX_DOCUMENT_BYTES
                    || !(contentType.equals("image/jpeg") || contentType.equals("image/png"))
                    || ImageIO.read(new ByteArrayInputStream(file.getBytes())) == null) {
                throw new BizException(400, label + "必须是 8MB 以内的 JPG 或 PNG 图片");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, label + "读取失败");
        }
    }

    private byte[] testDocument(String label, Long userId) throws Exception {
        BufferedImage image = new BufferedImage(900, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(245, 248, 252));
        graphics.fillRect(0, 0, 900, 560);
        graphics.setColor(new Color(16, 185, 180));
        graphics.fillRoundRect(70, 90, 760, 380, 32, 32);
        graphics.setColor(Color.WHITE);
        graphics.setFont(graphics.getFont().deriveFont(30f));
        graphics.drawString(label, 155, 255);
        graphics.setFont(graphics.getFont().deriveFont(22f));
        graphics.drawString("KOD 用户：" + userId, 315, 315);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String maskIdentity(String identityNo) {
        return identityNo.substring(0, 4) + "**********" + identityNo.substring(identityNo.length() - 4);
    }

    private void requireAdmin(Long userId) {
        String email = jdbc.queryForObject("SELECT email FROM sys_user WHERE id=?", String.class, userId);
        if (!properties.isAdminEmail(email)) throw new BizException(403, "需要算力中心管理员权限");
    }

    private void notifyAdmins(String type, String title, String content, String referenceType, String referenceId) {
        for (String email : properties.getAdminEmails()) {
            jdbc.query("SELECT id FROM sys_user WHERE LOWER(email)=?", rs -> {
                while (rs.next()) notifyUser(rs.getLong(1), type, title, content, referenceType, referenceId);
            }, email);
        }
    }

    private void notifyUser(long userId, String type, String title, String content,
                            String referenceType, String referenceId) {
        jdbc.update("""
                INSERT INTO compute_notification(user_id, notification_type, title, content, reference_type, reference_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, safe(type, 32), safe(title, 256), safe(content, 1000), referenceType, referenceId);
    }

    private String userEmail(Long userId) {
        return jdbc.queryForObject("SELECT email FROM sys_user WHERE id=?", String.class, userId);
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new BizException(404, "记录不存在");
        return rows.get(0);
    }

    private Long lastInsertId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private String safe(String value, int max) {
        String text = Objects.toString(value, "").trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    public record NodeInput(String nodeName, String region, String gpuModel, int gpuMemoryGb, int gpuCount,
                            String cpuDescription, int ramGb, int storageGb, String networkDescription) {
    }

    private static final class MediaTypes {
        private static final String PNG = "image/png";
        private static final String JPEG = "image/jpeg";
    }
}
