package com.kod.controller;

import com.kod.common.Result;
import com.kod.dto.LoginRequest;
import com.kod.dto.LoginResponse;
import com.kod.dto.RefreshTokenRequest;
import com.kod.dto.TokenRefreshResponse;
import com.kod.dto.KaiIdentityConfigResponse;
import com.kod.dto.KaiIdentityExchangeRequest;
import com.kod.dto.KaiIdentityLoginResponse;
import com.kod.dto.SendCodeRequest;
import com.kod.service.AuthService;
import com.kod.service.KaiIdentityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KaiIdentityService kaiIdentityService;

    /**
     * 登录或注册（首次登录即注册）。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("收到登录/注册请求，email={}", req.getEmail());
        return Result.ok(authService.loginOrRegister(req));
    }

    /**
     * 发送邮箱验证码（60s 内同邮箱只能发一次，5min 有效）。
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest req) {
        authService.sendCode(req.getEmail());
        return Result.ok(null);
    }

    /** Rotates a valid refresh token and returns a fresh KOD access session. */
    @PostMapping("/refresh")
    public Result<TokenRefreshResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return Result.ok(authService.refreshSession(req.refreshToken()));
    }

    /** Public OIDC settings used by the desktop and iOS PKCE flows. */
    @GetMapping("/kai/config")
    public Result<KaiIdentityConfigResponse> kaiConfig(
            @RequestParam(defaultValue = "desktop") String client) {
        return Result.ok(kaiIdentityService.getPublicConfig(client));
    }

    /** Exchanges a verified KAI Identity access token for the normal KOD JWT. */
    @PostMapping("/kai/exchange")
    public Result<KaiIdentityLoginResponse> kaiExchange(
            @Valid @RequestBody KaiIdentityExchangeRequest req) {
        return Result.ok(kaiIdentityService.exchange(req.getAccessToken()));
    }
}
