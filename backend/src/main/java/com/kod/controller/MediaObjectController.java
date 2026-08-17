package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.MediaMissingRequest;
import com.kod.service.MediaObjectService;
import com.kod.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/media/objects")
@RequiredArgsConstructor
public class MediaObjectController {
    private final MediaObjectService service;
    private final JwtUtil jwtUtil;

    @PostMapping("/missing")
    public Result<Map<String, List<String>>> missing(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MediaMissingRequest request) {
        return Result.ok(Map.of("missing", service.missing(user(authorization), request.keys())));
    }

    @PostMapping(consumes = "multipart/form-data")
    public Result<Map<String, Object>> upload(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("key") String key,
            @RequestPart("file") MultipartFile file) {
        return Result.ok(service.upload(user(authorization), key, file));
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> download(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String key) {
        try {
            var value = service.download(user(authorization), key);
            if (value.isEmpty()) return ResponseEntity.notFound().build();
            MediaObjectService.Download download = value.get();
            StreamingResponseBody body = output -> {
                try (download) {
                    download.stream().transferTo(output);
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(download.contentType()))
                    .contentLength(download.byteSize())
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                    .body(body);
        } catch (BizException error) {
            int status = error.getCode() >= 400 && error.getCode() <= 599 ? error.getCode() : 500;
            return ResponseEntity.status(status).build();
        }
    }

    private Long user(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "Missing or invalid Authorization header");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }
}
