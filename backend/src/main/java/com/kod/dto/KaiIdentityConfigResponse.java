package com.kod.dto;

public record KaiIdentityConfigResponse(
        boolean enabled,
        String clientId,
        String issuer,
        String authorizationEndpoint,
        String tokenEndpoint,
        String redirectUri,
        String scope) {
}
