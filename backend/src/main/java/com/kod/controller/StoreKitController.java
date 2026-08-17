package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.StoreKitNotificationRequest;
import com.kod.dto.StoreKitTransactionRequest;
import com.kod.service.StoreKitService;
import com.kod.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/storekit")
@RequiredArgsConstructor
public class StoreKitController {
    private final StoreKitService service;
    private final JwtUtil jwtUtil;

    @GetMapping("/account")
    public Result<Map<String, Object>> account(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.ok(service.account(parseUserId(authorization)));
    }

    @PostMapping("/transactions")
    public Result<Map<String, Object>> transaction(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody StoreKitTransactionRequest request) {
        return Result.ok(service.submit(parseUserId(authorization), request.signedTransaction()));
    }

    @PostMapping("/notifications/v2")
    public Result<Map<String, Object>> notification(@Valid @RequestBody StoreKitNotificationRequest request) {
        return Result.ok(service.notification(request.signedPayload()));
    }

    private Long parseUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
    }
}
