package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market-data.context")
public record MarketContextProperties(
        Duration overviewFreshness,
        Duration maxValidity,
        int historyLimit
) {
}
