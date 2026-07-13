package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        SchedulerProperties scheduler,
        RiskProperties risk,
        String mode
) {
    public record SchedulerProperties(boolean enabled) {}
}
