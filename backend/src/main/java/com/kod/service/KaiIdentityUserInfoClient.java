package com.kod.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.common.BizException;
import com.kod.config.IdentityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Calls the OIDC UserInfo endpoint so KOD never trusts renderer-provided claims. */
@Component
@RequiredArgsConstructor
public class KaiIdentityUserInfoClient {

    private final IdentityProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public UserInfo fetch(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUserInfoUri()))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BizException(401, "KAI Identity 登录凭证无效或已过期");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String subject = text(json, "sub");
            String email = text(json, "email");
            boolean emailVerified = json.path("email_verified").asBoolean(false);
            if (subject == null || email == null) {
                throw new BizException(401, "KAI Identity 未返回完整的用户标识和邮箱");
            }
            if (!emailVerified) {
                throw new BizException(403, "请先在 KAI Identity 完成邮箱验证");
            }
            return new UserInfo(subject, email.trim().toLowerCase());
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(503, "KAI Identity 验证被中断，请重试");
        } catch (Exception e) {
            throw new BizException(503, "暂时无法连接 KAI Identity，请稍后重试");
        }
    }

    private String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText();
    }

    public record UserInfo(String subject, String email) {
    }
}
