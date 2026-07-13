package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "external-api.opendart")
public record OpenDartProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        Duration corpCodeCacheTtl,
        String corpCodeSyncCron
) {
}
