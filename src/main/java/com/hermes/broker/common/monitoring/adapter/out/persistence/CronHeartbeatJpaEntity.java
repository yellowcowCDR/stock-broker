package com.hermes.broker.common.monitoring.adapter.out.persistence;

import com.hermes.broker.common.monitoring.CronHeartbeatPhase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "cron_heartbeat")
@Getter
@NoArgsConstructor
public class CronHeartbeatJpaEntity {

    @Id
    @Column(name = "cron_name", length = 100)
    private String cronName;

    @Column(name = "execution_id", nullable = false, length = 100)
    private String executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private CronHeartbeatPhase phase;

    @Column(name = "expected_interval_seconds", nullable = false)
    private long expectedIntervalSeconds;

    @Column(name = "last_started_at")
    private Instant lastStartedAt;

    @Column(name = "last_completed_at")
    private Instant lastCompletedAt;

    @Column(name = "expected_next_at", nullable = false)
    private Instant expectedNextAt;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    public CronHeartbeatJpaEntity(String cronName) {
        this.cronName = cronName;
    }

    public void apply(String executionId, CronHeartbeatPhase newPhase,
                      long intervalSeconds, String message, Instant receivedAt) {
        apply(executionId, newPhase, intervalSeconds,
                receivedAt.plusSeconds(intervalSeconds), message, receivedAt);
    }

    public void apply(String executionId, CronHeartbeatPhase newPhase,
                      long intervalSeconds, Instant nextExpectedAt,
                      String message, Instant receivedAt) {
        if (phase == CronHeartbeatPhase.STARTED
                && newPhase != CronHeartbeatPhase.STARTED
                && !this.executionId.equals(executionId)) {
            throw new IllegalArgumentException(
                    "Completion executionId does not match the active Cron execution.");
        }
        this.executionId = executionId;
        this.phase = newPhase;
        this.expectedIntervalSeconds = intervalSeconds;
        if (newPhase == CronHeartbeatPhase.STARTED) {
            this.lastStartedAt = receivedAt;
        } else {
            this.lastCompletedAt = receivedAt;
        }
        this.expectedNextAt = nextExpectedAt;
        this.message = message;
        this.updatedAt = receivedAt;
    }
}
