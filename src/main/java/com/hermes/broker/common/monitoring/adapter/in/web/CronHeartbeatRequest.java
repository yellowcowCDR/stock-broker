package com.hermes.broker.common.monitoring.adapter.in.web;

import com.hermes.broker.common.monitoring.CronHeartbeatPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CronHeartbeatRequest(
        @NotBlank @Size(max = 100) String cronName,
        @NotBlank @Size(max = 100) String executionId,
        @NotNull CronHeartbeatPhase phase,
        Long expectedIntervalSeconds,
        Instant expectedNextAt,
        @Size(max = 1000) String message
) {
}
