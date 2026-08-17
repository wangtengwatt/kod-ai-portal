package com.kod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.common.BizException;
import com.kod.config.CloudSandboxProperties;
import com.kod.dto.CloudOperationCreateRequest;
import com.kod.dto.CloudWorkspaceCreateRequest;
import com.kod.dto.WorkerCompleteRequest;
import com.kod.dto.WorkerEventRequest;
import com.kod.dto.WorkerPairRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CloudSandboxService {

    private static final Set<String> ALLOWED_KINDS = Set.of("exec", "read", "write", "edit", "ls", "grep", "find", "reset");
    private static final Set<String> ALLOWED_WORKER_EVENT_TYPES = Set.of(
            "worker_progress", "stdout", "stderr", "output_truncated");
    private static final int MAX_PARAMS_BYTES = 2 * 1024 * 1024;
    private static final int MAX_WORKER_EVENT_BYTES = 128 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final CloudSandboxProperties properties;
    private final CloudSandboxEventStreamService events;

    public Map<String, Object> availability() {
        if (!properties.isEnabled()) return Map.of("available", false, "reason", "云端沙箱尚未启用");
        long cutoff = System.currentTimeMillis() - properties.getWorkerHeartbeatTtlMillis();
        Integer online = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kod_worker_node WHERE status='online' AND last_seen_at>=?", Integer.class, cutoff);
        return online != null && online > 0
                ? Map.of("available", true, "onlineWorkers", online)
                : Map.of("available", false, "reason", "暂无在线 Worker");
    }

    public Map<String, Object> createWorkspace(Long userId, CloudWorkspaceCreateRequest request) {
        requireEnabled();
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO kod_cloud_workspace(id,user_id,working_directory,state,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                id, userId, request.workingDirectory(), "idle", now, now);
        audit(userId, "user", String.valueOf(userId), "workspace.create", "workspace", id,
                Map.of("workingDirectory", request.workingDirectory()));
        return Map.of("workspaceId", id, "state", "idle", "workingDirectory", request.workingDirectory(), "platform", "cloud");
    }

    public Map<String, Object> getWorkspace(Long userId, String workspaceId) {
        Map<String, Object> row = ownedWorkspace(userId, workspaceId);
        return Map.of(
                "workspaceId", row.get("id"),
                "state", row.get("state"),
                "workingDirectory", row.get("working_directory"),
                "platform", "cloud");
    }

    public Map<String, Object> enqueue(Long userId, String workspaceId, CloudOperationCreateRequest request) {
        requireEnabled();
        Map<String, Object> workspace = ownedWorkspace(userId, workspaceId);
        String kind = request.kind().toLowerCase(Locale.ROOT);
        if (!ALLOWED_KINDS.contains(kind)) throw new BizException(400, "不支持的云端操作类型");
        if ("worker_lost".equals(String.valueOf(workspace.get("state"))) && !"reset".equals(kind)) {
            throw new BizException(409, "工作区所在 Worker 已失联；请等待节点恢复后重置工作区");
        }
        String params = toJson(request.params());
        if (params.getBytes(StandardCharsets.UTF_8).length > MAX_PARAMS_BYTES) {
            throw new BizException(413, "任务参数超过 2 MiB 限制");
        }
        Integer queued = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kod_task_operation WHERE user_id=? AND state IN ('queued','running')",
                Integer.class, userId);
        if (queued != null && queued >= properties.getMaxQueuedOperationsPerUser()) {
            throw new BizException(429, "待执行任务过多，请稍后重试");
        }
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO kod_task_operation(id,workspace_id,user_id,kind,params_json,state,created_at) VALUES(?,?,?,?,?,'queued',?)",
                id, workspaceId, userId, kind, params, now);
        jdbc.update("UPDATE kod_cloud_workspace SET state='queued',updated_at=? WHERE id=? AND user_id=?", now, workspaceId, userId);
        events.append(userId, workspaceId, id, "queued", Map.of("kind", kind));
        audit(userId, "user", String.valueOf(userId), "operation.enqueue", "operation", id, Map.of("kind", kind));
        return operationResponse(id, "queued", null, null);
    }

    public Map<String, Object> getOperation(Long userId, String operationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,state,result_json,error_message FROM kod_task_operation WHERE id=? AND user_id=?",
                operationId, userId);
        if (rows.isEmpty()) throw new BizException(404, "任务不存在");
        Map<String, Object> row = rows.get(0);
        Object result = row.get("result_json") == null ? null : parseJson(String.valueOf(row.get("result_json")));
        return operationResponse(operationId, String.valueOf(row.get("state")), result,
                row.get("error_message") == null ? null : String.valueOf(row.get("error_message")));
    }

    public Map<String, Object> cancel(Long userId, String workspaceId, String operationId) {
        ownedWorkspace(userId, workspaceId);
        long now = System.currentTimeMillis();
        int changed;
        if (StringUtils.hasText(operationId)) {
            changed = jdbc.update("UPDATE kod_task_operation SET cancel_requested=TRUE,state=IF(state='queued','cancelled',state),finished_at=IF(state='queued',?,finished_at) WHERE id=? AND workspace_id=? AND user_id=? AND state IN ('queued','running')",
                    now, operationId, workspaceId, userId);
        } else {
            changed = jdbc.update("UPDATE kod_task_operation SET cancel_requested=TRUE,state=IF(state='queued','cancelled',state),finished_at=IF(state='queued',?,finished_at) WHERE workspace_id=? AND user_id=? AND state IN ('queued','running')",
                    now, workspaceId, userId);
        }
        if (changed > 0) {
            jdbc.update("""
                    UPDATE kod_cloud_workspace SET state=CASE
                      WHEN EXISTS(SELECT 1 FROM kod_task_operation WHERE workspace_id=? AND state='running') THEN 'running'
                      WHEN EXISTS(SELECT 1 FROM kod_task_operation WHERE workspace_id=? AND state='queued') THEN 'queued'
                      ELSE 'idle' END,updated_at=? WHERE id=? AND user_id=?
                    """, workspaceId, workspaceId, now, workspaceId, userId);
            events.append(userId, workspaceId, operationId, "cancel_requested", Map.of());
            audit(userId, "user", String.valueOf(userId), "operation.cancel", "workspace", workspaceId,
                    Map.of("operationId", operationId == null ? "" : operationId));
        }
        return Map.of("killed", changed > 0);
    }

    public String createPairingCode(String bootstrapSecret) {
        requireBootstrap(bootstrapSecret);
        String code = randomToken(18);
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO kod_worker_pair_code(code_hash,expires_at,created_at) VALUES(?,?,?)",
                hash(code), now + Duration.ofMinutes(10).toMillis(), now);
        audit(null, "bootstrap", "bootstrap", "worker.pair_code.create", "worker_pair_code", hash(code), Map.of());
        return code;
    }

    public Map<String, Object> pairWorker(WorkerPairRequest request) {
        requireEnabled();
        return transactions.execute(status -> {
            String codeHash = hash(request.code());
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT expires_at,used_at FROM kod_worker_pair_code WHERE code_hash=? FOR UPDATE", codeHash);
            long now = System.currentTimeMillis();
            if (rows.isEmpty() || rows.get(0).get("used_at") != null || ((Number) rows.get(0).get("expires_at")).longValue() < now) {
                throw new BizException(401, "Worker 配对码无效或已过期");
            }
            String workerId = UUID.randomUUID().toString();
            String token = randomToken(32);
            jdbc.update("UPDATE kod_worker_pair_code SET used_at=? WHERE code_hash=?", now, codeHash);
            jdbc.update("INSERT INTO kod_worker_node(id,name,token_hash,capabilities_json,status,last_seen_at,created_at) VALUES(?,?,?,?,?,?,?)",
                    workerId, request.name(), hash(token), toJson(request.capabilities()), "online", now, now);
            audit(null, "worker", workerId, "worker.pair", "worker", workerId, Map.of("name", request.name()));
            return Map.of("workerId", workerId, "workerToken", token);
        });
    }

    public Map<String, Object> heartbeat(String workerToken, JsonNode capabilities) {
        Map<String, Object> worker = authenticatedWorker(workerToken);
        long now = System.currentTimeMillis();
        String workerId = String.valueOf(worker.get("id"));
        if (capabilities == null) {
            jdbc.update("UPDATE kod_worker_node SET status='online',last_seen_at=? WHERE id=?", now, workerId);
        } else {
            jdbc.update("UPDATE kod_worker_node SET status='online',last_seen_at=?,capabilities_json=? WHERE id=?",
                    now, toJson(capabilities), workerId);
        }
        return Map.of("workerId", workerId, "serverTime", now);
    }

    public Map<String, Object> claim(String workerToken) {
        Map<String, Object> worker = authenticatedWorker(workerToken);
        String workerId = String.valueOf(worker.get("id"));
        return transactions.execute(status -> {
            long cutoff = System.currentTimeMillis() - properties.getWorkerHeartbeatTtlMillis();
            Number lastSeen = (Number) worker.get("last_seen_at");
            if (lastSeen.longValue() < cutoff) throw new BizException(401, "Worker 心跳已过期");
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT o.id,o.workspace_id,o.user_id,o.kind,o.params_json
                    FROM kod_task_operation o JOIN kod_cloud_workspace w ON w.id=o.workspace_id
                    WHERE o.state='queued' AND (w.worker_id IS NULL OR w.worker_id=?)
                    ORDER BY o.created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, workerId);
            if (rows.isEmpty()) return Map.of("operation", Map.of());
            Map<String, Object> row = rows.get(0);
            String operationId = String.valueOf(row.get("id"));
            String workspaceId = String.valueOf(row.get("workspace_id"));
            Long userId = ((Number) row.get("user_id")).longValue();
            long now = System.currentTimeMillis();
            int changed = jdbc.update("UPDATE kod_task_operation SET state='running',worker_id=?,started_at=? WHERE id=? AND state='queued'",
                    workerId, now, operationId);
            if (changed != 1) return Map.of("operation", Map.of());
            jdbc.update("UPDATE kod_cloud_workspace SET state='running',worker_id=?,updated_at=? WHERE id=?", workerId, now, workspaceId);
            events.append(userId, workspaceId, operationId, "started", Map.of("workerId", workerId));
            audit(userId, "worker", workerId, "operation.claim", "operation", operationId, Map.of());
            Map<String, Object> operation = new HashMap<>();
            operation.put("operationId", operationId);
            operation.put("workspaceId", workspaceId);
            operation.put("kind", row.get("kind"));
            operation.put("params", parseJson(String.valueOf(row.get("params_json"))));
            return Map.of("operation", operation);
        });
    }

    public Map<String, Object> appendWorkerEvent(String workerToken, String operationId, WorkerEventRequest request) {
        Map<String, Object> context = workerOperation(workerToken, operationId, true);
        if (!ALLOWED_WORKER_EVENT_TYPES.contains(request.type())) {
            throw new BizException(400, "不支持的 Worker 事件类型");
        }
        String payloadJson = toJson(request.payload() == null ? Map.of() : request.payload());
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_WORKER_EVENT_BYTES) {
            throw new BizException(413, "Worker 事件超过 128 KiB 限制");
        }
        events.append((Long) context.get("userId"), String.valueOf(context.get("workspaceId")), operationId,
                request.type(), request.payload());
        return Map.of("accepted", true);
    }

    public Map<String, Object> complete(String workerToken, String operationId, WorkerCompleteRequest request) {
        Map<String, Object> context = workerOperation(workerToken, operationId, true);
        boolean cancelled = Boolean.TRUE.equals(context.get("cancelRequested"));
        String state = cancelled ? "cancelled" : request.success() ? "succeeded" : "failed";
        String result = request.result() == null ? null : toJson(request.result());
        long now = System.currentTimeMillis();
        int changed = jdbc.update("UPDATE kod_task_operation SET state=?,result_json=?,error_message=?,finished_at=? WHERE id=? AND worker_id=? AND state='running'",
                state, result, request.error(), now, operationId, context.get("workerId"));
        if (changed != 1) throw new BizException(409, "任务已结束或不属于该 Worker");
        Long userId = (Long) context.get("userId");
        String workspaceId = String.valueOf(context.get("workspaceId"));
        if ("purge".equals(context.get("kind"))) {
            if (request.success() && !cancelled) {
                jdbc.update("DELETE FROM kod_task_event WHERE workspace_id=? AND user_id=?", workspaceId, userId);
                jdbc.update("DELETE FROM kod_task_operation WHERE workspace_id=? AND user_id=?", workspaceId, userId);
                jdbc.update("DELETE FROM kod_cloud_workspace WHERE id=? AND user_id=?", workspaceId, userId);
                jdbc.update("DELETE FROM kod_audit_event WHERE user_id=?", userId);
                Integer remaining = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM kod_cloud_workspace WHERE user_id=?", Integer.class, userId);
                if (remaining != null && remaining == 0) {
                    jdbc.update("UPDATE kod_account_deletion_receipt SET status='complete',completed_at=? WHERE user_id=?",
                            now, userId);
                }
            } else {
                jdbc.update("UPDATE kod_cloud_workspace SET state='purge_failed',updated_at=? WHERE id=?", now, workspaceId);
            }
            return Map.of("accepted", true);
        }
        jdbc.update("""
                UPDATE kod_cloud_workspace SET state=CASE
                  WHEN EXISTS(SELECT 1 FROM kod_task_operation WHERE workspace_id=? AND state='running') THEN 'running'
                  WHEN EXISTS(SELECT 1 FROM kod_task_operation WHERE workspace_id=? AND state='queued') THEN 'queued'
                  ELSE 'idle' END,updated_at=? WHERE id=?
                """, workspaceId, workspaceId, now, workspaceId);
        events.append(userId, workspaceId, operationId, state,
                request.success() && !cancelled ? request.result() : Map.of("error", cancelled ? "任务已取消" : request.error() == null ? "" : request.error()));
        audit(userId, "worker", String.valueOf(context.get("workerId")), "operation." + state, "operation", operationId, Map.of());
        return Map.of("accepted", true);
    }

    public Map<String, Object> cancellation(String workerToken, String operationId) {
        Map<String, Object> context = workerOperation(workerToken, operationId, false);
        return Map.of("cancelRequested", context.get("cancelRequested"));
    }

    public void assertOwnedWorkspace(Long userId, String workspaceId) {
        ownedWorkspace(userId, workspaceId);
    }

    public int recoverLostWorkers() {
        if (!properties.isEnabled()) return 0;
        long cutoff = System.currentTimeMillis() - properties.getWorkerHeartbeatTtlMillis();
        long now = System.currentTimeMillis();
        jdbc.update("UPDATE kod_worker_node SET status='offline' WHERE status='online' AND last_seen_at<?", cutoff);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.id,o.workspace_id,o.user_id,o.worker_id,o.kind,o.state
                FROM kod_task_operation o JOIN kod_worker_node w ON w.id=o.worker_id
                WHERE o.state IN ('queued','running') AND w.last_seen_at<?
                ORDER BY COALESCE(o.started_at,o.created_at) ASC LIMIT 100
                """, cutoff);
        int recovered = 0;
        for (Map<String, Object> row : rows) {
            String operationId = String.valueOf(row.get("id"));
            String workspaceId = String.valueOf(row.get("workspace_id"));
            String workerId = String.valueOf(row.get("worker_id"));
            Long userId = ((Number) row.get("user_id")).longValue();
            String kind = String.valueOf(row.get("kind"));
            String operationState = String.valueOf(row.get("state"));
            int changed;
            if ("purge".equals(kind)) {
                if (!"running".equals(operationState)) continue;
                changed = jdbc.update("""
                        UPDATE kod_task_operation o JOIN kod_worker_node w ON w.id=o.worker_id
                        SET o.state='queued',o.started_at=NULL
                        WHERE o.id=? AND o.state IN ('queued','running') AND o.worker_id=? AND w.last_seen_at<?
                        """, operationId, workerId, cutoff);
                if (changed == 1) {
                    jdbc.update("UPDATE kod_cloud_workspace SET state='purge_pending',updated_at=? WHERE id=?", now, workspaceId);
                    events.append(userId, workspaceId, operationId, "worker_lost",
                            Map.of("retry", true, "message", "Worker 失联；数据清理将在原节点恢复后重试"));
                }
            } else {
                changed = jdbc.update("""
                        UPDATE kod_task_operation o JOIN kod_worker_node w ON w.id=o.worker_id
                        SET o.state='failed',o.error_message='Workspace worker is offline',o.finished_at=?
                        WHERE o.id=? AND o.state IN ('queued','running') AND o.worker_id=? AND w.last_seen_at<?
                        """, now, operationId, workerId, cutoff);
                if (changed == 1) {
                    jdbc.update("UPDATE kod_cloud_workspace SET state='worker_lost',updated_at=? WHERE id=?", now, workspaceId);
                    events.append(userId, workspaceId, operationId, "worker_lost",
                            Map.of("retry", false, "message", "工作区所在 Worker 已失联"));
                }
            }
            if (changed == 1) {
                recovered++;
                audit(userId, "system", "recovery", "operation.worker_lost", "operation", operationId,
                        Map.of("workerId", workerId, "kind", kind));
            }
        }
        return recovered;
    }

    private Map<String, Object> workerOperation(String token, String operationId, boolean runningRequired) {
        Map<String, Object> worker = authenticatedWorker(token);
        String sql = "SELECT user_id,workspace_id,worker_id,cancel_requested,state,kind FROM kod_task_operation WHERE id=? AND worker_id=?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, operationId, worker.get("id"));
        if (rows.isEmpty()) throw new BizException(404, "Worker 任务不存在");
        Map<String, Object> row = rows.get(0);
        if (runningRequired && !"running".equals(String.valueOf(row.get("state")))) throw new BizException(409, "任务不在执行中");
        Map<String, Object> result = new HashMap<>();
        result.put("userId", ((Number) row.get("user_id")).longValue());
        result.put("workspaceId", row.get("workspace_id"));
        result.put("workerId", worker.get("id"));
        result.put("kind", row.get("kind"));
        result.put("cancelRequested", Boolean.TRUE.equals(row.get("cancel_requested")) || Integer.valueOf(1).equals(row.get("cancel_requested")));
        return result;
    }

    private Map<String, Object> authenticatedWorker(String token) {
        if (!StringUtils.hasText(token)) throw new BizException(401, "缺少 Worker 凭证");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,last_seen_at,status FROM kod_worker_node WHERE token_hash=?", hash(token));
        if (rows.isEmpty()) throw new BizException(401, "Worker 凭证无效");
        return rows.get(0);
    }

    private Map<String, Object> ownedWorkspace(Long userId, String workspaceId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,working_directory,state,worker_id FROM kod_cloud_workspace WHERE id=? AND user_id=?", workspaceId, userId);
        if (rows.isEmpty()) throw new BizException(404, "云端工作区不存在");
        return rows.get(0);
    }

    private Map<String, Object> operationResponse(String id, String state, Object result, String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("operationId", id);
        response.put("state", state);
        if (result != null) response.put("result", result);
        if (StringUtils.hasText(error)) response.put("error", error);
        return response;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new BizException(503, "云端沙箱尚未启用");
    }

    private void requireBootstrap(String supplied) {
        String configured = properties.getWorkerBootstrapSecret();
        if (!properties.isEnabled() || !StringUtils.hasText(configured)) throw new BizException(503, "Worker 配对未启用");
        if (!MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                (supplied == null ? "" : supplied).getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(401, "Worker Bootstrap 凭证无效");
        }
    }

    private void audit(Long userId, String actorType, String actorId, String action, String targetType, String targetId, Object metadata) {
        jdbc.update("INSERT INTO kod_audit_event(user_id,actor_type,actor_id,action_name,target_type,target_id,metadata_json,created_at) VALUES(?,?,?,?,?,?,?,?)",
                userId, actorType, actorId, action, targetType, targetId, toJson(metadata), System.currentTimeMillis());
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new BizException(400, "JSON 数据无效");
        }
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("数据库中的任务 JSON 损坏", error);
        }
    }
}
