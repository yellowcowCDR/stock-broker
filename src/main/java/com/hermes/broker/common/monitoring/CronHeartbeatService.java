package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.monitoring.adapter.out.persistence.CronHeartbeatJpaEntity;
import com.hermes.broker.common.monitoring.adapter.out.persistence.CronHeartbeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CronHeartbeatService {

    private static final long MIN_INTERVAL_SECONDS = 60;
    private static final long MAX_INTERVAL_SECONDS = 7 * 24 * 60 * 60;

    private final CronHeartbeatJpaRepository repository;
    private final OperationalEventRecorder recorder;
    private final Clock clock;

    @Transactional
    public CronHeartbeat record(String cronName, String executionId, CronHeartbeatPhase phase,
                                long expectedIntervalSeconds, String message) {
        return record(cronName, executionId, phase, expectedIntervalSeconds, null, message);
    }

    @Transactional
    public CronHeartbeat record(String cronName, String executionId, CronHeartbeatPhase phase,
                                Long expectedIntervalSeconds, Instant expectedNextAt,
                                String message) {
        Instant receivedAt = clock.instant();
        validateIdentity(cronName, executionId, phase, message);
        ExpectedSchedule schedule = resolveSchedule(
                expectedIntervalSeconds, expectedNextAt, receivedAt);
        CronHeartbeatJpaEntity entity = repository.findById(cronName).orElse(null);
        if (entity != null) {
            if (entity.getExecutionId().equals(executionId) && entity.getPhase() == phase) {
                boolean scheduleMismatch = expectedNextAt != null
                        ? !Objects.equals(entity.getExpectedNextAt(), schedule.expectedNextAt())
                        : entity.getExpectedIntervalSeconds() != schedule.intervalSeconds();
                if (scheduleMismatch
                        || !Objects.equals(entity.getMessage(), message)) {
                    throw new IllegalArgumentException(
                            "Heartbeat replay payload does not match the stored execution phase.");
                }
                return toDomain(entity);
            }
            if (entity.getExecutionId().equals(executionId)
                    && entity.getPhase() != CronHeartbeatPhase.STARTED) {
                throw new IllegalStateException("Cron execution is already terminal: " + executionId);
            }
            if (!entity.getExecutionId().equals(executionId)
                    && entity.getPhase() == CronHeartbeatPhase.STARTED) {
                throw new IllegalStateException(
                        "Another execution is already STARTED for Cron " + cronName + ".");
            }
        } else {
            entity = new CronHeartbeatJpaEntity(cronName);
        }
        entity.apply(executionId, phase, schedule.intervalSeconds(),
                schedule.expectedNextAt(), message, receivedAt);
        CronHeartbeat saved = toDomain(repository.save(entity));
        recorder.recordScheduledExecution("hermes:" + cronName,
                phase != CronHeartbeatPhase.FAILED,
                phase == CronHeartbeatPhase.FAILED ? message : null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CronHeartbeat> loadAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private void validateIdentity(String cronName, String executionId,
                                  CronHeartbeatPhase phase, String message) {
        if (cronName == null || cronName.isBlank() || cronName.length() > 100
                || executionId == null || executionId.isBlank() || executionId.length() > 100
                || phase == null) {
            throw new IllegalArgumentException("cronName, executionId and phase are required.");
        }
        if (message != null && message.length() > 1000) {
            throw new IllegalArgumentException("message must be 1000 characters or fewer.");
        }
    }

    private ExpectedSchedule resolveSchedule(
            Long requestedIntervalSeconds,
            Instant requestedNextAt,
            Instant receivedAt
    ) {
        if (requestedNextAt == null && requestedIntervalSeconds == null) {
            throw new IllegalArgumentException(
                    "expectedNextAt or expectedIntervalSeconds is required.");
        }
        if (requestedNextAt != null) {
            long secondsUntilNextRun = Duration.between(receivedAt, requestedNextAt).getSeconds();
            if (secondsUntilNextRun < 1 || secondsUntilNextRun > MAX_INTERVAL_SECONDS) {
                throw new IllegalArgumentException(
                        "expectedNextAt must be between 1 and 604800 seconds after Broker receipt time.");
            }
            return new ExpectedSchedule(secondsUntilNextRun, requestedNextAt);
        }
        long interval = requestedIntervalSeconds;
        if (interval < MIN_INTERVAL_SECONDS || interval > MAX_INTERVAL_SECONDS) {
            throw new IllegalArgumentException(
                    "expectedIntervalSeconds must be between 60 and 604800.");
        }
        return new ExpectedSchedule(interval, receivedAt.plusSeconds(interval));
    }

    private CronHeartbeat toDomain(CronHeartbeatJpaEntity entity) {
        return new CronHeartbeat(
                entity.getCronName(), entity.getExecutionId(), entity.getPhase(),
                entity.getExpectedIntervalSeconds(), entity.getLastStartedAt(),
                entity.getLastCompletedAt(), entity.getExpectedNextAt(), entity.getMessage(),
                entity.getUpdatedAt());
    }

    private record ExpectedSchedule(long intervalSeconds, Instant expectedNextAt) {
    }
}
