package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.property.KisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class KisTokenManager {

    private final RestClient restClient;
    private final KisProperties kisProperties;
    private final ConcurrentHashMap<String, String> tokenCache = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public KisTokenManager(RestClient.Builder restClientBuilder, KisProperties kisProperties) {
        this.kisProperties = kisProperties;
        this.restClient = restClientBuilder.build();
    }

    private String getCacheKey() {
        String appKey = kisProperties.api().appKey();
        return "KIS_TOKEN:" + kisProperties.environment().name() + ":" + Math.abs(appKey.hashCode());
    }

    public String getToken() {
        String cacheKey = getCacheKey();
        String token = tokenCache.get(cacheKey);
        if (token == null) {
            lock.lock();
            try {
                token = tokenCache.get(cacheKey);
                if (token == null) {
                    refreshToken();
                    token = tokenCache.get(cacheKey);
                }
            } finally {
                lock.unlock();
            }
        }
        return token;
    }

    // 매일 새벽 5시에 자동 갱신
    @Scheduled(cron = "0 0 5 * * *")
    public void refreshToken() {
        String appKey = kisProperties.api().appKey();
        String appSecret = kisProperties.api().appSecret();
        String baseUrl = kisProperties.baseUrl();
        
        Map<String, String> body = Map.of(
            "grant_type", "client_credentials",
            "appkey", appKey,
            "appsecret", appSecret
        );

        try {
            Map response = restClient.post()
                .uri(baseUrl + "/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            if (response != null && response.containsKey("access_token")) {
                tokenCache.put(getCacheKey(), (String) response.get("access_token"));
                log.info("KIS access token successfully refreshed for environment: {}", kisProperties.environment());
            } else {
                throw new RuntimeException("Failed to retrieve KIS access token: response missing access_token");
            }
        } catch (Exception e) {
            log.error("Failed to refresh KIS token for environment: {}", kisProperties.environment(), e);
            throw new RuntimeException("Failed to retrieve KIS access token", e);
        }
    }
}
