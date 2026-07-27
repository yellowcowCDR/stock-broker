package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.exception.CronExecutionConflictException;
import com.hermes.broker.common.monitoring.adapter.out.persistence.CronHeartbeatJpaEntity;
import com.hermes.broker.common.monitoring.adapter.out.persistence.CronHeartbeatJpaRepository;
import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.TradingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CronHeartbeatServiceTest {

    private static final Duration EXECUTION_LEASE = Duration.ofMinutes(30);

    @Mock CronHeartbeatJpaRepository repository;

    @Test
    void timestampsHeartbeatWithBrokerClockAndCalculatesNextExpectedRun() {
        Instant now = Instant.parse("2026-07-19T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        TradingProperties trading = new TradingProperties(
                null, "PAPER", AutonomyMode.ANALYSIS_ONLY,
                null, new TradingProperties.KillSwitchProperties(true), null);
        OperationalEventRecorder recorder = new OperationalEventRecorder(
                new SimpleMeterRegistry(), clock, trading);
        CronHeartbeatService service = new CronHeartbeatService(
                repository, recorder, clock, EXECUTION_LEASE);
        when(repository.findByCronNameForUpdate("market-analysis"))
                .thenReturn(Optional.empty());
        when(repository.save(any(CronHeartbeatJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CronHeartbeat result = service.record(
                "market-analysis", "run-1", CronHeartbeatPhase.SUCCEEDED,
                300, "completed");

        assertThat(result.updatedAt()).isEqualTo(now);
        assertThat(result.expectedNextAt()).isEqualTo(now.plusSeconds(300));
        assertThat(result.phase()).isEqualTo(CronHeartbeatPhase.SUCCEEDED);
    }

    @Test
    void replayOfSameHeartbeatDoesNotPostponeExpectedNextRun() {
        Instant firstReceivedAt = Instant.parse("2026-07-19T12:00:00Z");
        Clock clock = Clock.fixed(firstReceivedAt.plusSeconds(120), ZoneOffset.UTC);
        TradingProperties trading = new TradingProperties(
                null, "PAPER", AutonomyMode.ANALYSIS_ONLY,
                null, new TradingProperties.KillSwitchProperties(true), null);
        OperationalEventRecorder recorder = new OperationalEventRecorder(
                new SimpleMeterRegistry(), clock, trading);
        CronHeartbeatService service = new CronHeartbeatService(
                repository, recorder, clock, EXECUTION_LEASE);
        CronHeartbeatJpaEntity existing = new CronHeartbeatJpaEntity("market-analysis");
        existing.apply("run-1", CronHeartbeatPhase.SUCCEEDED, 300, "completed", firstReceivedAt);
        when(repository.findByCronNameForUpdate("market-analysis"))
                .thenReturn(Optional.of(existing));

        CronHeartbeat replay = service.record(
                "market-analysis", "run-1", CronHeartbeatPhase.SUCCEEDED,
                300, "completed");

        assertThat(replay.updatedAt()).isEqualTo(firstReceivedAt);
        assertThat(replay.expectedNextAt()).isEqualTo(firstReceivedAt.plusSeconds(300));
        verify(repository, times(0)).save(any());
    }

    @Test
    void explicitNextCronSlotIsPreservedAcrossCompletionHeartbeat() {
        Instant startedAt = Instant.parse("2026-07-17T05:50:00Z");
        Instant completedAt = Instant.parse("2026-07-17T05:51:00Z");
        Instant mondayFirstSlot = Instant.parse("2026-07-20T00:00:00Z");
        TradingProperties trading = new TradingProperties(
                null, "PAPER", AutonomyMode.ANALYSIS_ONLY,
                null, new TradingProperties.KillSwitchProperties(true), null);
        CronHeartbeatJpaEntity existing = new CronHeartbeatJpaEntity("hourly-market-analysis");
        existing.apply(
                "friday-last-run", CronHeartbeatPhase.STARTED,
                java.time.Duration.between(startedAt, mondayFirstSlot).getSeconds(),
                mondayFirstSlot, "started", startedAt);
        when(repository.findByCronNameForUpdate("hourly-market-analysis"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(CronHeartbeatJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Clock completionClock = Clock.fixed(completedAt, ZoneOffset.UTC);
        OperationalEventRecorder recorder = new OperationalEventRecorder(
                new SimpleMeterRegistry(), completionClock, trading);
        CronHeartbeatService service = new CronHeartbeatService(
                repository, recorder, completionClock, EXECUTION_LEASE);

        CronHeartbeat result = service.record(
                "hourly-market-analysis", "friday-last-run", CronHeartbeatPhase.SUCCEEDED,
                null, mondayFirstSlot, "completed");

        assertThat(result.expectedNextAt()).isEqualTo(mondayFirstSlot);
        assertThat(result.expectedIntervalSeconds()).isEqualTo(
                java.time.Duration.between(completedAt, mondayFirstSlot).getSeconds());
    }

    @Test
    void explicitNextSlotMustBeInTheFuture() {
        Instant now = Instant.parse("2026-07-19T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        TradingProperties trading = new TradingProperties(
                null, "PAPER", AutonomyMode.ANALYSIS_ONLY,
                null, new TradingProperties.KillSwitchProperties(true), null);
        CronHeartbeatService service = new CronHeartbeatService(
                repository,
                new OperationalEventRecorder(new SimpleMeterRegistry(), clock, trading),
                clock,
                EXECUTION_LEASE);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(
                        "market-analysis", "run-1", CronHeartbeatPhase.STARTED,
                        null, now, "started"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedNextAt");
    }

    @Test
    void activeLeaseBlocksAnotherExecutionWithConflictDetails() {
        Instant startedAt = Instant.parse("2026-07-19T12:00:00Z");
        Instant now = startedAt.plus(Duration.ofMinutes(10));
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        CronHeartbeatJpaEntity existing = started(
                "market-analysis", "run-1", startedAt,
                Instant.parse("2026-07-20T00:00:00Z"));
        when(repository.findByCronNameForUpdate("market-analysis"))
                .thenReturn(Optional.of(existing));
        CronHeartbeatService service = service(clock);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(
                        "market-analysis", "run-2", CronHeartbeatPhase.STARTED,
                        null, Instant.parse("2026-07-20T00:00:00Z"), "started"))
                .isInstanceOf(CronExecutionConflictException.class)
                .hasMessageContaining("run-1")
                .hasMessageContaining("leaseExpiresAt=2026-07-19T12:30:00Z");
    }

    @Test
    void expiredLeaseAllowsANewExecutionToTakeOver() {
        Instant startedAt = Instant.parse("2026-07-19T11:00:00Z");
        Instant now = Instant.parse("2026-07-19T12:00:00Z");
        Instant nextSlot = Instant.parse("2026-07-20T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        CronHeartbeatJpaEntity existing =
                started("market-analysis", "run-1", startedAt, nextSlot);
        when(repository.findByCronNameForUpdate("market-analysis"))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        CronHeartbeatService service = service(clock);

        CronHeartbeat takeover = service.record(
                "market-analysis", "run-2", CronHeartbeatPhase.STARTED,
                null, nextSlot, "started");

        assertThat(takeover.executionId()).isEqualTo("run-2");
        assertThat(takeover.phase()).isEqualTo(CronHeartbeatPhase.STARTED);
        assertThat(takeover.lastStartedAt()).isEqualTo(now);
        assertThat(takeover.leaseExpiresAt()).isEqualTo(now.plus(EXECUTION_LEASE));
    }

    @Test
    void replayOfStartedHeartbeatRenewsLeaseWithoutChangingOriginalStart() {
        Instant startedAt = Instant.parse("2026-07-19T12:00:00Z");
        Instant renewedAt = startedAt.plus(Duration.ofMinutes(10));
        Instant nextSlot = Instant.parse("2026-07-20T00:00:00Z");
        Clock clock = Clock.fixed(renewedAt, ZoneOffset.UTC);
        CronHeartbeatJpaEntity existing =
                started("market-analysis", "run-1", startedAt, nextSlot);
        when(repository.findByCronNameForUpdate("market-analysis"))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        CronHeartbeatService service = service(clock);

        CronHeartbeat renewed = service.record(
                "market-analysis", "run-1", CronHeartbeatPhase.STARTED,
                null, nextSlot, "started");

        assertThat(renewed.lastStartedAt()).isEqualTo(startedAt);
        assertThat(renewed.updatedAt()).isEqualTo(renewedAt);
        assertThat(renewed.leaseExpiresAt())
                .isEqualTo(renewedAt.plus(EXECUTION_LEASE));
    }

    @Test
    void terminalHeartbeatForUnknownExecutionCannotOverwriteCurrentState() {
        Instant now = Instant.parse("2026-07-19T12:00:00Z");
        Instant nextSlot = Instant.parse("2026-07-20T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        CronHeartbeatJpaEntity existing = new CronHeartbeatJpaEntity("market-analysis");
        existing.apply("run-1", CronHeartbeatPhase.SUCCEEDED, 300,
                nextSlot, "completed", now.minusSeconds(60));
        when(repository.findByCronNameForUpdate("market-analysis"))
                .thenReturn(Optional.of(existing));
        CronHeartbeatService service = service(clock);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(
                        "market-analysis", "stale-run", CronHeartbeatPhase.FAILED,
                        null, nextSlot, "late failure"))
                .isInstanceOf(CronExecutionConflictException.class)
                .hasMessageContaining("must enter STARTED");
    }

    private CronHeartbeatJpaEntity started(
            String cronName,
            String executionId,
            Instant startedAt,
            Instant expectedNextAt) {
        CronHeartbeatJpaEntity entity = new CronHeartbeatJpaEntity(cronName);
        entity.apply(executionId, CronHeartbeatPhase.STARTED,
                Duration.between(startedAt, expectedNextAt).getSeconds(),
                expectedNextAt, "started", startedAt);
        return entity;
    }

    private CronHeartbeatService service(Clock clock) {
        TradingProperties trading = new TradingProperties(
                null, "PAPER", AutonomyMode.ANALYSIS_ONLY,
                null, new TradingProperties.KillSwitchProperties(true), null);
        OperationalEventRecorder recorder = new OperationalEventRecorder(
                new SimpleMeterRegistry(), clock, trading);
        return new CronHeartbeatService(
                repository, recorder, clock, EXECUTION_LEASE);
    }
}
