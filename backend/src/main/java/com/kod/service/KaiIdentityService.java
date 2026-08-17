package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kod.common.BizException;
import com.kod.config.IdentityProperties;
import com.kod.dto.KaiIdentityConfigResponse;
import com.kod.dto.KaiIdentityLoginResponse;
import com.kod.entity.User;
import com.kod.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KaiIdentityService {

    private final IdentityProperties properties;
    private final KaiIdentityUserInfoClient userInfoClient;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public KaiIdentityConfigResponse getPublicConfig(String client) {
        boolean ios = "ios".equalsIgnoreCase(client);
        String clientId = ios ? properties.getIosClientId() : properties.getClientId();
        String redirectUri = ios ? properties.getIosRedirectUri() : properties.getRedirectUri();
        return new KaiIdentityConfigResponse(
                properties.isEnabled() && StringUtils.hasText(clientId),
                clientId,
                properties.getIssuer(),
                properties.getAuthorizationEndpoint(),
                properties.getTokenEndpoint(),
                redirectUri,
                properties.getScope());
    }

    @Transactional(rollbackFor = Exception.class)
    public KaiIdentityLoginResponse exchange(String accessToken) {
        if (!isConfigured()) {
            throw new BizException(503, "KAI 统一登录尚未完成客户端配置");
        }
        KaiIdentityUserInfoClient.UserInfo info = userInfoClient.fetch(accessToken);
        User user = findLinkedUser(info.subject());
        boolean newUser = false;
        if (user == null) {
            user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, info.email()));
            if (user == null) {
                try {
                    user = authService.provisionIdentityUser(info.email());
                    newUser = true;
                } catch (DuplicateKeyException e) {
                    // Two first-login requests can race. Reuse the account created by the winner.
                    user = userMapper.selectOne(
                            Wrappers.<User>lambdaQuery().eq(User::getEmail, info.email()));
                    if (user == null) {
                        throw e;
                    }
                }
            }
            bind(user, info);
        } else {
            touchLink(user.getId(), info);
        }
        return new KaiIdentityLoginResponse(
                authService.issueTokenForUser(user.getId()),
                authService.issueRefreshTokenForUser(user.getId()),
                newUser,
                user.getEmail());
    }

    private boolean isConfigured() {
        return properties.isEnabled() && StringUtils.hasText(properties.getClientId());
    }

    private User findLinkedUser(String subject) {
        List<Long> ids = jdbc.query(
                "SELECT user_id FROM sys_user_identity WHERE issuer=? AND subject=? LIMIT 1",
                (rs, rowNum) -> rs.getLong(1),
                properties.getIssuer(), subject);
        return ids.isEmpty() ? null : userMapper.selectById(ids.get(0));
    }

    private void bind(User user, KaiIdentityUserInfoClient.UserInfo info) {
        List<String> subjects = jdbc.query(
                "SELECT subject FROM sys_user_identity WHERE user_id=? AND issuer=? LIMIT 1",
                (rs, rowNum) -> rs.getString(1),
                user.getId(), properties.getIssuer());
        if (!subjects.isEmpty() && !subjects.get(0).equals(info.subject())) {
            throw new BizException(409, "该 KOD 账号已绑定其他 KAI Identity 账号");
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("""
                            INSERT INTO sys_user_identity
                            (user_id, issuer, subject, email, create_time, last_login_time)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    user.getId(), properties.getIssuer(), info.subject(), info.email(),
                    Timestamp.valueOf(now), Timestamp.valueOf(now));
        } catch (DuplicateKeyException e) {
            User linked = findLinkedUser(info.subject());
            if (linked == null || !linked.getId().equals(user.getId())) {
                throw new BizException(409, "该 KAI Identity 账号已绑定其他 KOD 账号");
            }
            touchLink(user.getId(), info);
        }
    }

    private void touchLink(Long userId, KaiIdentityUserInfoClient.UserInfo info) {
        jdbc.update("""
                        UPDATE sys_user_identity
                        SET email=?, last_login_time=?
                        WHERE user_id=? AND issuer=? AND subject=?
                        """,
                info.email(), Timestamp.valueOf(LocalDateTime.now()), userId,
                properties.getIssuer(), info.subject());
    }
}
