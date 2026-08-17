package com.kod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class CloudSandboxEventStreamService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public long append(Long userId, String workspaceId, String operationId, String type, Object payload) {
        long now = System.currentTimeMillis();
        String json = toJson(payload == null ? Map.of() : payload);
        jdbc.update("INSERT INTO kod_task_event(user_id, workspace_id, operation_id, event_type, payload_json, created_at) VALUES(?,?,?,?,?,?)",
                userId, workspaceId, operationId, type, json, now);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> event = Map.of(
                "id", id == null ? 0L : id,
                "operationId", operationId == null ? "" : operationId,
                "type", type,
                "payload", payload == null ? Map.of() : payload,
                "createdAt", now);
        broadcast(key(userId, workspaceId), type, event);
        return id == null ? 0L : id;
    }

    public SseEmitter subscribe(Long userId, String workspaceId, long after) {
        String key = key(userId, workspaceId);
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(key, list);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        List<Map<String, Object>> backlog = jdbc.queryForList(
                "SELECT id, operation_id, event_type, payload_json, created_at FROM kod_task_event WHERE user_id=? AND workspace_id=? AND id>? ORDER BY id ASC LIMIT 500",
                userId, workspaceId, Math.max(0, after));
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("workspaceId", workspaceId)));
            for (Map<String, Object> row : backlog) {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(row.get("id")))
                        .name(String.valueOf(row.get("event_type")))
                        .data(Map.of(
                                "id", row.get("id"),
                                "operationId", row.get("operation_id") == null ? "" : row.get("operation_id"),
                                "type", row.get("event_type"),
                                "payload", parseJson(String.valueOf(row.get("payload_json"))),
                                "createdAt", row.get("created_at"))));
            }
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private void broadcast(String key, String type, Map<String, Object> event) {
        List<SseEmitter> list = emitters.get(key);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().id(String.valueOf(event.get("id"))).name(type).data(event));
            } catch (IOException error) {
                emitter.completeWithError(error);
            }
        }
    }

    private String key(Long userId, String workspaceId) {
        return userId + ":" + workspaceId;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("无法序列化任务事件", error);
        }
    }

    private Object parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException error) {
            return Map.of();
        }
    }
}
