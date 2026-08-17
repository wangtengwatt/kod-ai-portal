package com.kod.service;

import com.kod.common.BizException;
import com.kod.dto.AccountDeleteRequest;
import com.kod.entity.User;
import com.kod.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private static final String TOKEN_KEY_PREFIX = "kod:token:";

    private final JdbcTemplate jdbc;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final MediaObjectService mediaObjectService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> delete(Long userId, AccountDeleteRequest request) {
        if (!"DELETE".equals(request.confirmation())) {
            throw new BizException(400, "请输入 DELETE 确认删除账户");
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(404, "账户不存在");

        Integer identityLinks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_identity WHERE user_id=?", Integer.class, userId);
        boolean identityAccount = identityLinks != null && identityLinks > 0;
        if (!identityAccount && (!StringUtils.hasText(request.password())
                || !passwordEncoder.matches(request.password(), user.getPassword()))) {
            throw new BizException(401, "当前密码错误");
        }

        long now = System.currentTimeMillis();
        List<String> workspaces = jdbc.queryForList(
                "SELECT id FROM kod_cloud_workspace WHERE user_id=?", String.class, userId);
        jdbc.update("UPDATE kod_task_operation SET cancel_requested=TRUE,state=IF(state='queued','cancelled',state),finished_at=IF(state='queued',?,finished_at) WHERE user_id=? AND state IN ('queued','running')",
                now, userId);
        for (String workspaceId : workspaces) {
            String operationId = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO kod_task_operation(id,workspace_id,user_id,kind,params_json,state,cancel_requested,created_at) VALUES(?,?,?,?,?,'queued',FALSE,?)",
                    operationId, workspaceId, userId, "purge", "{}", now);
            jdbc.update("UPDATE kod_cloud_workspace SET state='queued',updated_at=? WHERE id=? AND user_id=?",
                    now, workspaceId, userId);
        }

        deleteKnowledgeBase(userId);
        deleteSyncData(userId);
        mediaObjectService.markUserForDeletion(userId);
        jdbc.update("DELETE FROM kod_ios_store_account WHERE user_id=?", userId);
        jdbc.update("DELETE FROM compute_notification WHERE user_id=?", userId);
        jdbc.update("DELETE FROM coupons WHERE user_id=?", userId);
        jdbc.update("DELETE FROM api_request_logs WHERE user_id=?", userId);
        jdbc.update("DELETE FROM dashboard_hourly WHERE user_id=?", userId);
        jdbc.update("DELETE FROM dashboard_model_summary WHERE user_id=?", userId);
        jdbc.update("DELETE FROM log_sync_state WHERE user_id=?", userId);
        jdbc.update("DELETE FROM compute_referral_profile WHERE user_id=?", userId);
        jdbc.update("DELETE FROM compute_referral_binding WHERE inviter_user_id=? OR invitee_user_id=?", userId, userId);
        jdbc.update("UPDATE compute_identity_verification SET real_name_ciphertext=NULL,identity_no_ciphertext=NULL,identity_no_masked='',front_file_id=NULL,back_file_id=NULL,rejection_reason='',status='CLOSED',closed_at=NOW(),purge_after=NOW(),purged_at=NOW() WHERE user_id=?",
                userId);
        jdbc.update("UPDATE compute_supplier SET display_name='Deleted account',contact='',description='',rejection_reason='' WHERE user_id=?",
                userId);
        jdbc.update("DELETE FROM sys_user_identity WHERE user_id=?", userId);

        if (user.getConnect() != null) {
            jdbc.update("UPDATE relay_station_key SET status=0 WHERE id=?", user.getConnect());
        }
        String deletedEmail = "deleted+" + UUID.randomUUID() + "@invalid.kod.local";
        String disabledPassword = passwordEncoder.encode("deleted:" + UUID.randomUUID());
        jdbc.update("UPDATE sys_user SET email=?,password=?,station_id=NULL,connect=NULL WHERE id=?",
                deletedEmail, disabledPassword, userId);

        String status = workspaces.isEmpty() ? "complete" : "sandbox_purge_pending";
        Long completedAt = workspaces.isEmpty() ? now : null;
        jdbc.update("""
                INSERT INTO kod_account_deletion_receipt(user_id,status,requested_at,completed_at)
                VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),requested_at=VALUES(requested_at),completed_at=VALUES(completed_at)
                """, userId, status, now, completedAt);
        try {
            redis.delete(TOKEN_KEY_PREFIX + userId);
        } catch (Exception ignored) {
            // JWT tombstone enforcement below does not depend on Redis availability.
        }
        return Map.of("deleted", true, "sandboxPurgePending", !workspaces.isEmpty());
    }

    private void deleteKnowledgeBase(Long userId) {
        jdbc.update("DELETE FROM kod_knowledge_base_chunk WHERE user_id=?", userId);
        jdbc.update("DELETE FROM kod_knowledge_base_file WHERE user_id=?", userId);
        jdbc.update("DELETE FROM kod_knowledge_base WHERE user_id=?", userId);
    }

    private void deleteSyncData(Long userId) {
        jdbc.update("DELETE FROM kod_sync_mutation WHERE user_id=?", userId);
        jdbc.update("DELETE FROM kod_sync_event WHERE user_id=?", userId);
        jdbc.update("DELETE FROM kod_sync_record WHERE user_id=?", userId);
        jdbc.update("DELETE FROM kod_secret_vault WHERE user_id=?", userId);
    }
}
