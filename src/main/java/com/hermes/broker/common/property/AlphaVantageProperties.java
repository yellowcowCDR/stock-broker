package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "external-api.alpha-vantage")
public record AlphaVantageProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        Duration cacheTtl,
        Duration freshnessThreshold,
        String earningsHorizon
) {
}
