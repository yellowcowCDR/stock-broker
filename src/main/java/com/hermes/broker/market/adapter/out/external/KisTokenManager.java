package com.hermes.broker.market.adapter.out.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KisTokenManager {

    private final RestClient restClient;
    private final AtomicReference<String> tokenCache = new AtomicReference<>();

    @Value("${kis.api.base-url}")
    private String baseUrl;

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    public KisTokenManager(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String getToken() {
        String token = tokenCache.get();
        if (token == null) {
            refreshToken();
            token = tokenCache.get();
        }
        return token;
    }

    // 매일 새벽 5시에 자동 갱신
    @Scheduled(cron = "0 0 5 * * *")
    public void refreshToken() {
        Map<String, String> body = Map.of(
            "grant_type", "client_credentials",
            "appkey", appKey,
            "appsecret", appSecret
        );

        Map response = restClient.post()
            .uri(baseUrl + "/oauth2/tokenP")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);

        if (response != null && response.containsKey("access_token")) {
            tokenCache.set((String) response.get("access_token"));
        } else {
            throw new RuntimeException("Failed to retrieve KIS access token");
        }
    }
}
