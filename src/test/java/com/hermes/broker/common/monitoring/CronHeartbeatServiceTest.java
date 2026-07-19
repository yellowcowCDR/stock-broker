package com.hermes.broker.common.monitoring;

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CronHeartbeatServiceTest {

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
        CronHeartbeatService service = new CronHeartbeatService(repository, recorder, clock);
        when(repository.findById("market-analysis")).thenReturn(Optional.empty());
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
        CronHeartbeatService service = new CronHeartbeatService(repository, recorder, clock);
        CronHeartbeatJpaEntity existing = new CronHeartbeatJpaEntity("market-analysis");
        existing.apply("run-1", CronHeartbeatPhase.SUCCEEDED, 300, "completed", firstReceivedAt);
        when(repository.findById("market-analysis")).thenReturn(Optional.of(existing));

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
        when(repository.findById("hourly-market-analysis")).thenReturn(Optional.of(existing));
        when(repository.save(any(CronHeartbeatJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Clock completionClock = Clock.fixed(completedAt, ZoneOffset.UTC);
        OperationalEventRecorder recorder = new OperationalEventRecorder(
                new SimpleMeterRegistry(), completionClock, trading);
        CronHeartbeatService service = new CronHeartbeatService(
                repository, recorder, completionClock);

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
                clock);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(
                        "market-analysis", "run-1", CronHeartbeatPhase.STARTED,
                        null, now, "started"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedNextAt");
    }
}
