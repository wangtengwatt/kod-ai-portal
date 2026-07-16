package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项，绑定 {@code kod.jwt.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "kod.jwt")
public class JwtProperties {

    /** HS256 签名密钥（长度需 >= 32 字节）。 */
    private String secret;

    /** token 有效期（毫秒）。 */
    private long expireMillis = 604800000L;
}
