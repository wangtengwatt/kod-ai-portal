package com.kod.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kod.common.BizException;
import com.kod.dto.LoginRequest;
import com.kod.dto.LoginResponse;
import com.kod.entity.RelayStation;
import com.kod.entity.User;
import com.kod.config.JwtProperties;
import com.kod.mapper.UserMapper;
import com.kod.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 认证服务：登录或注册（首次登录即注册）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Redis 中 token 缓存 key 前缀。 */
    private static final String TOKEN_KEY_PREFIX = "kod:token:";

    private final UserMapper userMapper;
    private final RelayStationService relayStationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 登录或注册：
     * <ul>
     *   <li>用户不存在 → 注册（必须提供有效邀请码，据此关联中转站），返回 token；</li>
     *   <li>用户已存在 → 校验密码，邀请码不生效，返回 token。</li>
     * </ul>
     *
     * @param req 请求
     * @return 登录响应（token + 是否新注册）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginOrRegister(LoginRequest req) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getEmail, req.getEmail()));

        if (user == null) {
            // 首次登录即注册
            log.info("用户不存在，执行注册，email={}", req.getEmail());
            if (!StringUtils.hasText(req.getInviteCode())) {
                throw new BizException(400, "首次登录（注册）必须输入邀请码");
            }
            RelayStation station = relayStationService.findByInviteCode(req.getInviteCode());
            if (station == null) {
                throw new BizException(400, "邀请码无效：" + req.getInviteCode());
            }
            user = new User();
            user.setEmail(req.getEmail());
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            user.setStationId(station.getId());
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            log.info("注册成功，userId={}, 关联 stationId={}", user.getId(), station.getId());
            return new LoginResponse(issueToken(user.getId()), true);
        }

        // 已存在用户：校验密码，邀请码不生效
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("登录失败，密码错误，email={}", req.getEmail());
            throw new BizException(401, "邮箱或密码错误");
        }
        log.info("登录成功，userId={}", user.getId());
        return new LoginResponse(issueToken(user.getId()), false);
    }

    /**
     * 生成 token，并尽力将其写入 Redis（带与 token 一致的 TTL）。
     *
     * <p>Redis 写入为尽力而为：即便 Redis 不可用也不影响登录返回 token。</p>
     *
     * @param userId 用户主键
     * @return JWT token
     */
    private String issueToken(Long userId) {
        String token = jwtUtil.generateToken(userId);
        try {
            stringRedisTemplate.opsForValue().set(
                    TOKEN_KEY_PREFIX + userId,
                    token,
                    Duration.ofMillis(jwtProperties.getExpireMillis()));
            log.debug("token 已写入 Redis，userId={}", userId);
        } catch (Exception e) {
            log.warn("写入 Redis token 失败（不影响登录）：{}", e.getMessage());
        }
        return token;
    }
}
