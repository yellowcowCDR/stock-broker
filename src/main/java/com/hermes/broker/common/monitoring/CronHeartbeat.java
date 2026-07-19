package com.hermes.broker.common.monitoring;

import java.time.Instant;

public record CronHeartbeat(
        String cronName,
        String executionId,
        CronHeartbeatPhase phase,
        long expectedIntervalSeconds,
        Instant lastStartedAt,
        Instant lastCompletedAt,
        Instant expectedNextAt,
        String message,
        Instant updatedAt
) {
}
