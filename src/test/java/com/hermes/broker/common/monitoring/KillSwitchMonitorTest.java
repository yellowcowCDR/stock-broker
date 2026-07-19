package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.monitoring.adapter.out.persistence.BrokerRuntimeStateJpaEntity;
import com.hermes.broker.common.monitoring.adapter.out.persistence.BrokerRuntimeStateJpaRepository;
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

@ExtendWith(MockitoExtension.class)
class KillSwitchMonitorTest {

    @Mock BrokerRuntimeStateJpaRepository repository;

    @Test
    void detectsKillSwitchDisableAcrossBrokerRestart() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);
        TradingProperties trading = new TradingProperties(
                null, "PAPER", AutonomyMode.PAPER_AUTO,
                null, new TradingProperties.KillSwitchProperties(false), null);
        OperationalEventRecorder recorder = new OperationalEventRecorder(
                new SimpleMeterRegistry(), clock, trading);
        BrokerRuntimeStateJpaEntity previous = new BrokerRuntimeStateJpaEntity(
                "entry-kill-switch-enabled", "true", clock.instant().minusSeconds(60));
        when(repository.findById("entry-kill-switch-enabled")).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        KillSwitchMonitor monitor = new KillSwitchMonitor(repository, trading, recorder, clock);

        monitor.captureStartupState();

        assertThat(recorder.eventSnapshot()).containsKey("kill-switch-change");
        assertThat(previous.getStateValue()).isEqualTo("false");
    }
}
