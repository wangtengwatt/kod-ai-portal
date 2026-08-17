package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** KAI Identity OIDC configuration. */
@Data
@Component
@ConfigurationProperties(prefix = "kod.identity")
public class IdentityProperties {

    private boolean enabled = true;
    private String clientId = "";
    private String iosClientId = "";
    private String issuer = "https://auth.kai.com/api/auth";
    private String authorizationEndpoint = "https://auth.kai.com/api/auth/oauth2/authorize";
    private String tokenEndpoint = "https://auth.kai.com/api/auth/oauth2/token";
    private String userInfoUri = "https://auth.kai.com/api/auth/oauth2/userinfo";
    private String redirectUri = "http://127.0.0.1:1456/auth/callback";
    private String iosRedirectUri = "https://kod.kai.com/auth/ios/callback";
    private String scope = "openid profile email";
    private boolean schemaInitEnabled = true;
}
