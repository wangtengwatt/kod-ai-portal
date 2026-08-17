package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.KnowledgeBaseChunkReadRequest;
import com.kod.dto.KnowledgeBaseCreateRequest;
import com.kod.dto.KnowledgeBaseUpdateRequest;
import com.kod.service.KnowledgeBaseService;
import com.kod.util.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;
    private final JwtUtil jwtUtil;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestHeader("Authorization") String authorization) {
        return Result.ok(service.list(user(authorization)));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestHeader("Authorization") String authorization,
                                              @Valid @RequestBody KnowledgeBaseCreateRequest request) {
        service.create(user(authorization), request);
        return Result.ok(Map.of("created", true));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                              @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        service.update(user(authorization), id, request);
        return Result.ok(Map.of("updated", true));
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        service.delete(user(authorization), id);
        return Result.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/files")
    public Result<List<Map<String, Object>>> files(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                                   @RequestParam(defaultValue = "0") @Min(0) int offset,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return Result.ok(service.listFiles(user(authorization), id, offset, limit));
    }

    @GetMapping("/{id}/files/count")
    public Result<Long> count(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return Result.ok(service.countFiles(user(authorization), id));
    }

    @PostMapping(value = "/{id}/files", consumes = "multipart/form-data")
    public Result<Map<String, Object>> upload(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                              @RequestPart("file") MultipartFile file) {
        service.upload(user(authorization), id, file);
        return Result.ok(Map.of("uploaded", true));
    }

    @DeleteMapping("/files/{fileId}")
    public Result<Map<String, Object>> deleteFile(@RequestHeader("Authorization") String authorization, @PathVariable long fileId) {
        service.deleteFile(user(authorization), fileId);
        return Result.ok(Map.of("deleted", true));
    }

    @PostMapping("/files/{fileId}/retry")
    public Result<Map<String, Object>> retry(@RequestHeader("Authorization") String authorization, @PathVariable long fileId) {
        service.retry(user(authorization), fileId);
        return Result.ok(Map.of("retried", true));
    }

    @PostMapping("/files/{fileId}/pause")
    public Result<Map<String, Object>> pause(@RequestHeader("Authorization") String authorization, @PathVariable long fileId) {
        service.pause(user(authorization), fileId);
        return Result.ok(Map.of("paused", true));
    }

    @PostMapping("/files/{fileId}/resume")
    public Result<Map<String, Object>> resume(@RequestHeader("Authorization") String authorization, @PathVariable long fileId) {
        service.resume(user(authorization), fileId);
        return Result.ok(Map.of("resumed", true));
    }

    @GetMapping("/{id}/search")
    public Result<List<Map<String, Object>>> search(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                                    @RequestParam @NotBlank @Size(max = 2000) String query) {
        return Result.ok(service.search(user(authorization), id, query));
    }

    @PostMapping("/{id}/file-metas")
    public Result<List<Map<String, Object>>> metas(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                                   @RequestBody List<Long> fileIds) {
        return Result.ok(service.fileMetas(user(authorization), id, fileIds));
    }

    @PostMapping("/{id}/chunks")
    public Result<List<Map<String, Object>>> chunks(@RequestHeader("Authorization") String authorization, @PathVariable long id,
                                                    @Valid @RequestBody KnowledgeBaseChunkReadRequest request) {
        return Result.ok(service.readChunks(user(authorization), id, request));
    }

    @PostMapping("/parser/test")
    public Result<Map<String, Object>> parserTest(@RequestHeader("Authorization") String authorization) {
        user(authorization);
        return Result.ok(Map.of("success", true, "parser", "server-tika"));
    }

    private Long user(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }
}
