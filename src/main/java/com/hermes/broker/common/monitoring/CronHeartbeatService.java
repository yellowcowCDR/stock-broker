package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.exception.CronExecutionConflictException;
import com.hermes.broker.common.monitoring.adapter.out.persistence.CronHeartbeatJpaEntity;
import com.hermes.broker.common.monitoring.adapter.out.persistence.CronHeartbeatJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class CronHeartbeatService {

    private static final long MIN_INTERVAL_SECONDS = 60;
    private static final long MAX_INTERVAL_SECONDS = 7 * 24 * 60 * 60;

    private final CronHeartbeatJpaRepository repository;
    private final OperationalEventRecorder recorder;
    private final Clock clock;
    private final Duration executionLease;

    public CronHeartbeatService(
            CronHeartbeatJpaRepository repository,
            OperationalEventRecorder recorder,
            Clock clock,
            @Value("${broker.monitoring.cron-execution-lease:30m}") Duration executionLease) {
        if (executionLease == null || executionLease.isZero() || executionLease.isNegative()) {
            throw new IllegalArgumentException("Cron execution lease must be positive.");
        }
        this.repository = repository;
        this.recorder = recorder;
        this.clock = clock;
        this.executionLease = executionLease;
    }

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
        CronHeartbeatJpaEntity entity =
                repository.findByCronNameForUpdate(cronName).orElse(null);
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
                if (phase == CronHeartbeatPhase.STARTED) {
                    entity.renewLease(receivedAt.plus(executionLease), receivedAt);
                    return toDomain(repository.save(entity));
                }
                return toDomain(entity);
            }
            if (entity.getExecutionId().equals(executionId)
                    && entity.getPhase() != CronHeartbeatPhase.STARTED) {
                throw new CronExecutionConflictException(
                        "Cron execution is already terminal: " + executionId);
            }
            if (!entity.getExecutionId().equals(executionId)
                    && entity.getPhase() == CronHeartbeatPhase.STARTED) {
                if (phase != CronHeartbeatPhase.STARTED) {
                    throw activeExecutionConflict(cronName, entity);
                }
                Instant leaseExpiresAt = effectiveLeaseExpiresAt(entity);
                if (leaseExpiresAt == null || receivedAt.isBefore(leaseExpiresAt)) {
                    throw activeExecutionConflict(cronName, entity);
                }
                String expiredMessage = "Cron execution lease expired: cronName=" + cronName
                        + ", executionId=" + entity.getExecutionId()
                        + ", startedAt=" + entity.getLastStartedAt()
                        + ", leaseExpiresAt=" + leaseExpiresAt;
                log.error("{}; accepting takeover executionId={}",
                        expiredMessage, executionId);
                recorder.recordScheduledExecution(
                        "hermes:" + cronName, false, expiredMessage);
            } else if (!entity.getExecutionId().equals(executionId)
                    && phase != CronHeartbeatPhase.STARTED) {
                throw new CronExecutionConflictException(
                        "Cron execution must enter STARTED before a terminal heartbeat: "
                                + executionId);
            }
        } else {
            entity = new CronHeartbeatJpaEntity(cronName);
        }
        Instant leaseExpiresAt = phase == CronHeartbeatPhase.STARTED
                ? receivedAt.plus(executionLease) : null;
        entity.apply(executionId, phase, schedule.intervalSeconds(),
                schedule.expectedNextAt(), leaseExpiresAt, message, receivedAt);
        CronHeartbeat saved = toDomain(repository.save(entity));
        if (phase != CronHeartbeatPhase.STARTED) {
            recorder.recordScheduledExecution("hermes:" + cronName,
                    phase == CronHeartbeatPhase.SUCCEEDED,
                    phase == CronHeartbeatPhase.FAILED ? message : null);
        }
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
                entity.getLastCompletedAt(), effectiveLeaseExpiresAt(entity),
                entity.getExpectedNextAt(), entity.getMessage(), entity.getUpdatedAt());
    }

    private Instant effectiveLeaseExpiresAt(CronHeartbeatJpaEntity entity) {
        if (entity.getPhase() != CronHeartbeatPhase.STARTED) {
            return null;
        }
        if (entity.getLeaseExpiresAt() != null) {
            return entity.getLeaseExpiresAt();
        }
        return entity.getLastStartedAt() == null
                ? null : entity.getLastStartedAt().plus(executionLease);
    }

    private CronExecutionConflictException activeExecutionConflict(
            String cronName, CronHeartbeatJpaEntity entity) {
        return new CronExecutionConflictException(
                "Another execution is already STARTED for Cron " + cronName
                        + ": executionId=" + entity.getExecutionId()
                        + ", startedAt=" + entity.getLastStartedAt()
                        + ", leaseExpiresAt=" + effectiveLeaseExpiresAt(entity));
    }

    private record ExpectedSchedule(long intervalSeconds, Instant expectedNextAt) {
    }
}
