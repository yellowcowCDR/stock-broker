package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "external-api.naver-news")
public record NaverNewsProperties(
        boolean enabled,
        String baseUrl,
        String clientId,
        String clientSecret,
        Duration connectTimeout,
        Duration readTimeout,
        int defaultDisplay,
        int maxDisplay,
        Duration cacheTtl
) {
}
