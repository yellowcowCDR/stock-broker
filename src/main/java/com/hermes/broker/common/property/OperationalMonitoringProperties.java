package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "broker.monitoring")
public record OperationalMonitoringProperties(
        boolean enabled,
        Duration contextWarningBefore,
        Duration newsMaxAge,
        Duration watchlistMaxAge,
        Duration overviewMaxAge,
        Duration externalFailureAlertWindow,
        Duration reconciliationFailureAlertWindow,
        Duration databaseFailureAlertWindow,
        Duration cronGrace
) {
}
