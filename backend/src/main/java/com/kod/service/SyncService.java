package com.kod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.common.BizException;
import com.kod.config.SyncProperties;
import com.kod.dto.SyncChangeResponse;
import com.kod.dto.SyncExchangeRequest;
import com.kod.dto.SyncExchangeResponse;
import com.kod.dto.SyncMutationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PULL_CHANGES = 1000;
    private static final Set<String> ENTITY_TYPES = Set.of(
            "session", "task", "image_generation", "video_generation",
            "model_settings", "skill_settings");
    private static final Set<String> LOSSLESS_CONFLICT_TYPES = Set.of(
            "session", "task", "image_generation", "video_generation");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SyncSecretVaultService vault;
    private final SyncProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public SyncExchangeResponse exchange(Long userId, SyncExchangeRequest request) {
        if (!properties.isEnabled()) throw new BizException(503, "跨设备同步服务尚未启用");

        List<String> acknowledged = new ArrayList<>();
        for (SyncMutationRequest mutation : request.mutations()) {
            validateMutation(request.deviceId(), mutation);
            Long existingCursor = findMutationCursor(userId, mutation.mutationId());
            if (existingCursor == null) {
                long eventCursor = applyMutation(userId, mutation);
                jdbc.update("""
                        INSERT INTO kod_sync_mutation (user_id, mutation_id, event_cursor, created_at)
                        VALUES (?, ?, ?, ?)
                        """, userId, mutation.mutationId(), eventCursor, System.currentTimeMillis());
            }
            acknowledged.add(mutation.mutationId());
        }

        List<SyncChangeResponse> changes = pullChanges(userId, request.cursor());
        long cursor = request.cursor();
        if (!changes.isEmpty()) cursor = changes.get(changes.size() - 1).cursor();
        return new SyncExchangeResponse(cursor, acknowledged, changes);
    }

    private void validateMutation(String requestDeviceId, SyncMutationRequest mutation) {
        if (!requestDeviceId.equals(mutation.deviceId())) {
            throw new BizException(400, "同步 mutation 的 deviceId 不匹配");
        }
        if (!ENTITY_TYPES.contains(mutation.entityType())) {
            throw new BizException(400, "不支持的同步实体类型");
        }
        if (mutation.baseRevision() != null && mutation.baseRevision() < 0) {
            throw new BizException(400, "同步 baseRevision 无效");
        }
        boolean modelSettings = "model_settings".equals(mutation.entityType());
        if (modelSettings != mutation.sensitive()) {
            throw new BizException(400, modelSettings
                    ? "模型提供商配置必须进入加密保险库"
                    : "该实体类型不得写入密钥保险库");
        }
        if (!mutation.deleted() && (mutation.payload() == null || mutation.payload().isNull())) {
            throw new BizException(400, "同步 upsert 缺少 payload");
        }
    }

    private Long findMutationCursor(Long userId, String mutationId) {
        List<Long> values = jdbc.query(
                "SELECT event_cursor FROM kod_sync_mutation WHERE user_id=? AND mutation_id=? LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), userId, mutationId);
        return values.isEmpty() ? null : values.get(0);
    }

    private long applyMutation(Long userId, SyncMutationRequest mutation) {
        RecordRow current = findRecordForUpdate(userId, mutation.entityType(), mutation.entityId());
        boolean revisionMatches = current == null
                ? mutation.baseRevision() == null || mutation.baseRevision() == 0
                : mutation.baseRevision() != null && mutation.baseRevision() == current.revision();

        if (revisionMatches) {
            return writeRecord(userId, mutation.entityType(), mutation.entityId(),
                    current == null ? 1 : current.revision() + 1, mutation);
        }

        if (LOSSLESS_CONFLICT_TYPES.contains(mutation.entityType()) && !mutation.deleted()) {
            String conflictId = conflictEntityId(mutation.entityId(), mutation.deviceId());
            return writeRecord(userId, mutation.entityType(), conflictId, 1, mutation);
        }

        if (current != null && mutation.clientUpdatedAt() > current.clientUpdatedAt()) {
            return writeRecord(userId, mutation.entityType(), mutation.entityId(), current.revision() + 1, mutation);
        }

        if (current != null) return appendEvent(userId, current);
        return writeRecord(userId, mutation.entityType(), mutation.entityId(), 1, mutation);
    }

    private RecordRow findRecordForUpdate(Long userId, String entityType, String entityId) {
        List<RecordRow> rows = jdbc.query("""
                        SELECT entity_type, entity_id, revision, client_updated_at, server_updated_at,
                               source_device_id, deleted, is_sensitive, payload_json, secret_id
                        FROM kod_sync_record
                        WHERE user_id=? AND entity_type=? AND entity_id=?
                        FOR UPDATE
                        """, this::mapRecord, userId, entityType, entityId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long writeRecord(Long userId, String entityType, String entityId,
                             long revision, SyncMutationRequest mutation) {
        long now = System.currentTimeMillis();
        String payloadJson = mutation.deleted() ? null : serializePayload(mutation.payload());
        String secretId = null;
        if (mutation.sensitive() && !mutation.deleted()) {
            secretId = vault.store(userId, payloadJson);
            payloadJson = null;
        }
        jdbc.update("""
                INSERT INTO kod_sync_record
                  (user_id, entity_type, entity_id, revision, client_updated_at, server_updated_at,
                   source_device_id, deleted, is_sensitive, payload_json, secret_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  revision=VALUES(revision), client_updated_at=VALUES(client_updated_at),
                  server_updated_at=VALUES(server_updated_at), source_device_id=VALUES(source_device_id),
                  deleted=VALUES(deleted), is_sensitive=VALUES(is_sensitive),
                  payload_json=VALUES(payload_json), secret_id=VALUES(secret_id)
                """, userId, entityType, entityId, revision, mutation.clientUpdatedAt(), now,
                mutation.deviceId(), mutation.deleted(), mutation.sensitive(), payloadJson, secretId);
        return appendEvent(userId, new RecordRow(entityType, entityId, revision,
                mutation.clientUpdatedAt(), now, mutation.deviceId(), mutation.deleted(),
                mutation.sensitive(), payloadJson, secretId));
    }

    private long appendEvent(Long userId, RecordRow row) {
        jdbc.update("""
                INSERT INTO kod_sync_event
                  (user_id, entity_type, entity_id, revision, client_updated_at, server_updated_at,
                   source_device_id, deleted, is_sensitive, payload_json, secret_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, row.entityType(), row.entityId(), row.revision(), row.clientUpdatedAt(),
                row.serverUpdatedAt(), row.sourceDeviceId(), row.deleted(), row.sensitive(),
                row.payloadJson(), row.secretId());
        Long cursor = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (cursor == null) throw new BizException(500, "无法记录同步事件");
        return cursor;
    }

    private List<SyncChangeResponse> pullChanges(Long userId, long cursor) {
        return jdbc.query("""
                        SELECT id, entity_type, entity_id, revision, client_updated_at, server_updated_at,
                               source_device_id, deleted, is_sensitive, payload_json, secret_id
                        FROM kod_sync_event
                        WHERE user_id=? AND id>?
                        ORDER BY id ASC LIMIT ?
                        """, (rs, rowNum) -> {
                    boolean sensitive = rs.getBoolean("is_sensitive");
                    boolean deleted = rs.getBoolean("deleted");
                    String payloadJson = rs.getString("payload_json");
                    String secretId = rs.getString("secret_id");
                    JsonNode payload = null;
                    if (!deleted) {
                        if (sensitive) payloadJson = vault.read(userId, secretId);
                        payload = deserializePayload(payloadJson);
                    }
                    return new SyncChangeResponse(
                            rs.getLong("id"), rs.getString("entity_type"), rs.getString("entity_id"),
                            rs.getLong("revision"), rs.getLong("client_updated_at"),
                            rs.getLong("server_updated_at"), rs.getString("source_device_id"),
                            deleted, sensitive, payload);
                }, userId, cursor, MAX_PULL_CHANGES);
    }

    private RecordRow mapRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RecordRow(
                rs.getString("entity_type"), rs.getString("entity_id"), rs.getLong("revision"),
                rs.getLong("client_updated_at"), rs.getLong("server_updated_at"),
                rs.getString("source_device_id"), rs.getBoolean("deleted"),
                rs.getBoolean("is_sensitive"), rs.getString("payload_json"), rs.getString("secret_id"));
    }

    private String serializePayload(JsonNode payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw new BizException(413, "单条同步数据超过 2 MiB 限制");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new BizException(400, "同步 payload 不是有效 JSON");
        }
    }

    private JsonNode deserializePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "同步事件 payload 已损坏");
        }
    }

    private String conflictEntityId(String entityId, String deviceId) {
        String safeDevice = deviceId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (safeDevice.length() > 8) safeDevice = safeDevice.substring(0, 8);
        String suffix = "~conflict~" + safeDevice + "-" + UUID.randomUUID().toString().substring(0, 8);
        int maxPrefix = Math.max(1, 191 - suffix.length());
        return entityId.substring(0, Math.min(entityId.length(), maxPrefix)) + suffix;
    }

    private record RecordRow(
            String entityType,
            String entityId,
            long revision,
            long clientUpdatedAt,
            long serverUpdatedAt,
            String sourceDeviceId,
            boolean deleted,
            boolean sensitive,
            String payloadJson,
            String secretId) {
    }
}
