package com.hermes.broker.common.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OperationalStatus(
        Instant checkedAt,
        boolean killSwitchEnabled,
        List<OperationalAlert> activeAlerts,
        Map<String, SourceObservation> sources,
        List<CronHeartbeat> cronHeartbeats
) {
}
