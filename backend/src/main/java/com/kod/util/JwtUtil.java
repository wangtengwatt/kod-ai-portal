package com.kod.util;

import com.kod.common.BizException;
import com.kod.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：生成与解析 token（HS256，subject=userId）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties props;

    /** 签名密钥，由配置的 secret 派生。 */
    private SecretKey key;

    /** 初始化签名密钥。 */
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        log.info("JWT 工具初始化完成，token 有效期={}ms", props.getExpireMillis());
    }

    /**
     * 生成 token。
     *
     * @param userId 用户主键
     * @return 签名后的 JWT
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getExpireMillis());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token 得到用户主键。
     *
     * @param token JWT
     * @return 用户主键
     * @throws BizException token 无效或已过期时抛出（401）
     */
    public Long parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.valueOf(subject);
        } catch (Exception e) {
            log.warn("token 解析失败：{}", e.getMessage());
            throw new BizException(401, "token 无效或已过期");
        }
    }
}
