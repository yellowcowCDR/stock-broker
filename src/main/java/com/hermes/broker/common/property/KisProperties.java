package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kis")
public record KisProperties(
        KisEnvironment environment,
        String baseUrl,
        KisProductionRateLimitType productionRateLimitType,
        Api api,
        Account account,
        RateLimit rateLimit,
        Retry retry,
        Token token,
        Queue queue
) {
    public record Api(
            String appKey,
            String appSecret,
            String accountNo,
            Duration connectTimeout,
            Duration readTimeout
    ) {
    }

    public record Account(
            String rateLimitKey
    ) {
    }

    public record RateLimit(
            boolean enabled,
            int requestsPerSecond,
            Duration minimumInterval,
            Duration acquireTimeout
    ) {
    }

    public record Retry(
            RetryPolicy query,
            RetryPolicy order
    ) {
    }

    public record RetryPolicy(
            int maxAttempts,
            Duration initialDelay,
            double multiplier,
            Duration maxDelay,
            Duration jitterMin,
            Duration jitterMax
    ) {
    }

    public record Token(
            Duration minimumIssueInterval,
            Duration refreshBeforeExpiration
    ) {
    }

    public record Queue(
            int maxSize
    ) {
    }
}
