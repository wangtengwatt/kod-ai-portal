package com.kod.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.CloudOperationCreateRequest;
import com.kod.dto.CloudWorkspaceCreateRequest;
import com.kod.dto.WorkerCompleteRequest;
import com.kod.dto.WorkerEventRequest;
import com.kod.dto.WorkerPairRequest;
import com.kod.service.CloudSandboxEventStreamService;
import com.kod.service.CloudSandboxService;
import com.kod.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/cloud-sandbox")
@RequiredArgsConstructor
public class CloudSandboxController {

    private final CloudSandboxService service;
    private final CloudSandboxEventStreamService eventStreams;
    private final JwtUtil jwtUtil;

    @GetMapping("/availability")
    public Result<Map<String, Object>> availability(@RequestHeader(value = "Authorization", required = false) String authorization) {
        parseUserId(authorization);
        return Result.ok(service.availability());
    }

    @PostMapping("/workspaces")
    public Result<Map<String, Object>> createWorkspace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CloudWorkspaceCreateRequest request) {
        return Result.ok(service.createWorkspace(parseUserId(authorization), request));
    }

    @GetMapping("/workspaces/{workspaceId}")
    public Result<Map<String, Object>> workspace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String workspaceId) {
        return Result.ok(service.getWorkspace(parseUserId(authorization), workspaceId));
    }

    @PostMapping("/workspaces/{workspaceId}/operations")
    public Result<Map<String, Object>> enqueue(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String workspaceId,
            @Valid @RequestBody CloudOperationCreateRequest request) {
        return Result.ok(service.enqueue(parseUserId(authorization), workspaceId, request));
    }

    @GetMapping("/operations/{operationId}")
    public Result<Map<String, Object>> operation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String operationId) {
        return Result.ok(service.getOperation(parseUserId(authorization), operationId));
    }

    @PostMapping("/workspaces/{workspaceId}/cancel")
    public Result<Map<String, Object>> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String workspaceId,
            @RequestBody(required = false) Map<String, Object> body) {
        Object value = body == null ? null : body.get("operationId");
        return Result.ok(service.cancel(parseUserId(authorization), workspaceId, value == null ? null : String.valueOf(value)));
    }

    @GetMapping(value = "/workspaces/{workspaceId}/events", produces = "text/event-stream")
    public SseEmitter events(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "0") long after) {
        Long userId = parseUserId(authorization);
        service.assertOwnedWorkspace(userId, workspaceId);
        return eventStreams.subscribe(userId, workspaceId, after);
    }

    @PostMapping("/workers/pairing-codes")
    public Result<Map<String, Object>> pairingCode(
            @RequestHeader(value = "X-KOD-Worker-Bootstrap", required = false) String bootstrap) {
        return Result.ok(Map.of("code", service.createPairingCode(bootstrap), "expiresInSeconds", 600));
    }

    @PostMapping("/workers/pair")
    public Result<Map<String, Object>> pair(@Valid @RequestBody WorkerPairRequest request) {
        return Result.ok(service.pairWorker(request));
    }

    @PostMapping("/workers/heartbeat")
    public Result<Map<String, Object>> heartbeat(
            @RequestHeader(value = "X-KOD-Worker-Token", required = false) String token,
            @RequestBody(required = false) JsonNode capabilities) {
        return Result.ok(service.heartbeat(token, capabilities));
    }

    @PostMapping("/workers/claim")
    public Result<Map<String, Object>> claim(
            @RequestHeader(value = "X-KOD-Worker-Token", required = false) String token) {
        return Result.ok(service.claim(token));
    }

    @PostMapping("/workers/operations/{operationId}/events")
    public Result<Map<String, Object>> workerEvent(
            @RequestHeader(value = "X-KOD-Worker-Token", required = false) String token,
            @PathVariable String operationId,
            @Valid @RequestBody WorkerEventRequest request) {
        return Result.ok(service.appendWorkerEvent(token, operationId, request));
    }

    @PostMapping("/workers/operations/{operationId}/complete")
    public Result<Map<String, Object>> complete(
            @RequestHeader(value = "X-KOD-Worker-Token", required = false) String token,
            @PathVariable String operationId,
            @Valid @RequestBody WorkerCompleteRequest request) {
        return Result.ok(service.complete(token, operationId, request));
    }

    @GetMapping("/workers/operations/{operationId}/cancellation")
    public Result<Map<String, Object>> cancellation(
            @RequestHeader(value = "X-KOD-Worker-Token", required = false) String token,
            @PathVariable String operationId) {
        return Result.ok(service.cancellation(token, operationId));
    }

    private Long parseUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }
}
