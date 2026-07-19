package com.hermes.broker.common.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("brokerOperational")
@RequiredArgsConstructor
public class BrokerOperationalHealthIndicator implements HealthIndicator {

    private final OperationalStatusService statusService;

    @Override
    public Health health() {
        OperationalStatus status = statusService.getStatus();
        long critical = status.activeAlerts().stream()
                .filter(alert -> alert.severity() == AlertSeverity.CRITICAL).count();
        Health.Builder builder = critical > 0 ? Health.down() : Health.up();
        return builder
                .withDetail("criticalAlertCount", critical)
                .withDetail("warningAlertCount", status.activeAlerts().size() - critical)
                .withDetail("killSwitchEnabled", status.killSwitchEnabled())
                .withDetail("alerts", status.activeAlerts())
                .build();
    }
}
