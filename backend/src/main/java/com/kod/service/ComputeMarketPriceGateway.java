package com.kod.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kod.config.ComputeMarketPriceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 第三方行情 HTTP 网关。认证密钥只在服务端请求头中使用。 */
@Component
@RequiredArgsConstructor
public class ComputeMarketPriceGateway {

    private static final String USER_AGENT = "KOD-Compute-Market/0.1 (+https://kod.kai.com)";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final ComputeMarketPriceProperties properties;

    public JsonNode fetchVastOffers() throws Exception {
        Map<String, Object> payload = Map.of(
                "limit", 1000,
                "type", "on-demand",
                "verified", Map.of("eq", true),
                "rentable", Map.of("eq", true),
                "rented", Map.of("eq", false));
        String body = restClientBuilder.build().post()
                .uri(properties.getVastSearchUrl())
                .header("Authorization", "Bearer " + properties.getVastApiKey())
                .header("User-Agent", USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body == null ? "{}" : body);
    }

    public JsonNode fetchAkamaiTypes() throws Exception {
        String body = restClientBuilder.build().get().uri(properties.getAkamaiTypesUrl())
                .header("User-Agent", USER_AGENT)
                .retrieve().body(String.class);
        return objectMapper.readTree(body == null ? "{}" : body);
    }
}
