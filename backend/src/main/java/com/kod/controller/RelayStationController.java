package com.kod.controller;

import com.kod.common.BizException;
import com.kod.common.Result;
import com.kod.dto.RelayConfigResponse;
import com.kod.dto.SaveApiKeyRequest;
import com.kod.dto.SaveRelayStationRequest;
import com.kod.service.RelayStationService;
import com.kod.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 中转站接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/relay-station")
@RequiredArgsConstructor
public class RelayStationController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RelayStationService relayStationService;
    private final JwtUtil jwtUtil;

    /**
     * 保存中转站信息（url + 邀请码 → 第一表；apiKey → 第二表）。
     *
     * @param req 保存请求
     * @return 新建的中转站主键
     */
    @PostMapping
    public Result<Long> save(@Valid @RequestBody SaveRelayStationRequest req) {
        log.info("收到保存中转站请求，inviteCode={}", req.getInviteCode());
        return Result.ok(relayStationService.saveStation(req));
    }

    /**
     * 凭 JWT token 获取当前用户关联的中转站 url 与 API 密钥。
     *
     * @param authorization 请求头 {@code Authorization: Bearer <token>}
     * @return 中转站配置
     */
    @GetMapping("/config")
    public Result<RelayConfigResponse> config(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = parseUserId(authorization);
        log.info("收到获取中转站配置请求，userId={}", userId);
        return Result.ok(relayStationService.getConfigByUserId(userId));
    }

    /**
     * 已登录用户为自己关联的中转站配置/更新 API Key。
     *
     * @param authorization 请求头 {@code Authorization: Bearer <token>}
     * @param req           包含 apiKey 的请求体
     * @return 空 data，code=0 表示成功
     */
    @PostMapping("/api-key")
    public Result<Void> saveApiKey(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SaveApiKeyRequest req) {
        Long userId = parseUserId(authorization);
        log.info("收到保存 API Key 请求，userId={}", userId);
        relayStationService.saveApiKey(userId, req.getApiKey());
        return Result.ok(null);
    }

    /* ---------- helper ---------- */

    private Long parseUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BizException(401, "缺少或非法的 Authorization 头");
        }
        return jwtUtil.parseUserId(authorization.substring(BEARER_PREFIX.length()));
    }
}
