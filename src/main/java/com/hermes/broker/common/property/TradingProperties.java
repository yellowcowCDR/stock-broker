package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        SchedulerProperties scheduler,
        String mode,
        AutonomyMode autonomyMode,
        RealOrderProperties realOrder,
        KillSwitchProperties killSwitch,
        OverseasOrderProperties overseasOrder
) {
    public record SchedulerProperties(boolean enabled) {}
    public record RealOrderProperties(boolean enabled) {}
    public record KillSwitchProperties(boolean enabled) {}
    public record OverseasOrderProperties(boolean enabled) {}
}
