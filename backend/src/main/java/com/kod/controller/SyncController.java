package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.SyncExchangeRequest;
import com.kod.dto.SyncExchangeResponse;
import com.kod.service.SyncService;
import com.kod.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final JwtUtil jwtUtil;

    @PostMapping("/exchange")
    public Result<SyncExchangeResponse> exchange(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SyncExchangeRequest request) {
        return Result.ok(syncService.exchange(parseUserId(authorization), request));
    }

    private Long parseUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }
}
