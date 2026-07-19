package com.hermes.broker.common.monitoring;

import java.time.Instant;
import java.util.Map;

public record OperationalAlert(
        String code,
        AlertSeverity severity,
        String message,
        Instant detectedAt,
        Map<String, Object> details
) {
    public OperationalAlert {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
