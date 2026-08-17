package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.AccountDeleteRequest;
import com.kod.service.AccountDeletionService;
import com.kod.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountDeletionService service;
    private final JwtUtil jwtUtil;

    @DeleteMapping
    public Result<Map<String, Object>> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AccountDeleteRequest request) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        Long userId = jwtUtil.parseUserId(authorization.substring("Bearer ".length()));
        return Result.ok(service.delete(userId, request));
    }
}
