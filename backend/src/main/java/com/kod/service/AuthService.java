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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 认证服务：登录 / 注册（首次登录即注册）/ 邮箱验证码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_KEY_PREFIX = "kod:token:";
    private static final String CODE_KEY_PREFIX = "kod:code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration CODE_RATE_LIMIT = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final RelayStationService relayStationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final EmailService emailService;

    /**
     * 发送邮箱验证码。
     * 限制：同一邮箱 60s 只能发一次，验证码 5min 有效。
     */
    public void sendCode(String email) {
        // 60s 防刷
        String rateKey = CODE_KEY_PREFIX + "rate:" + email;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(rateKey))) {
            throw new BizException(429, "验证码发送过于频繁，请 60 秒后再试");
        }

        // 生成 6 位数字验证码
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeKey = CODE_KEY_PREFIX + email;

        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
        stringRedisTemplate.opsForValue().set(rateKey, "1", CODE_RATE_LIMIT);

        // 发送邮件（尽力而为，失败不影响验证码存储）
        emailService.sendCode(email, code);

        log.info("验证码已生成，email={}, code={}, 有效期=5min", email, code);
    }

    /**
     * 校验邮箱验证码，校验通过后立即删除（一次性）。
     */
    private void verifyCode(String email, String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(400, "首次登录（注册）必须输入邮箱验证码");
        }
        String codeKey = CODE_KEY_PREFIX + email;
        String stored = stringRedisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BizException(400, "验证码不存在或已过期");
        }
        if (!stored.equals(code)) {
            throw new BizException(400, "验证码错误");
        }
        stringRedisTemplate.delete(codeKey);
    }

    /**
     * 登录或注册：
     * <ul>
     *   <li>用户不存在 → 先校验验证码和邀请码，然后注册并返回 token；</li>
     *   <li>用户已存在 → 校验密码，邀请码和验证码不生效，返回 token。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginOrRegister(LoginRequest req) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getEmail, req.getEmail()));

        if (user == null) {
            // 首次登录即注册：校验验证码 + 邀请码
            log.info("用户不存在，执行注册，email={}", req.getEmail());
            verifyCode(req.getEmail(), req.getEmailCode());
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

        // 已存在用户：校验密码
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("登录失败，密码错误，email={}", req.getEmail());
            throw new BizException(401, "邮箱或密码错误");
        }
        log.info("登录成功，userId={}", user.getId());
        return new LoginResponse(issueToken(user.getId()), false);
    }

    private String issueToken(Long userId) {
        String token = jwtUtil.generateToken(userId);
        try {
            stringRedisTemplate.opsForValue().set(
                    TOKEN_KEY_PREFIX + userId,
                    token,
                    Duration.ofMillis(jwtProperties.getExpireMillis()));
        } catch (Exception e) {
            log.warn("写入 Redis token 失败（不影响登录）：{}", e.getMessage());
        }
        return token;
    }
}
