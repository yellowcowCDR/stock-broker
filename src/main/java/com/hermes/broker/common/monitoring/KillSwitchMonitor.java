package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.monitoring.adapter.out.persistence.BrokerRuntimeStateJpaEntity;
import com.hermes.broker.common.monitoring.adapter.out.persistence.BrokerRuntimeStateJpaRepository;
import com.hermes.broker.common.property.TradingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class KillSwitchMonitor {

    private static final String STATE_KEY = "entry-kill-switch-enabled";

    private final BrokerRuntimeStateJpaRepository repository;
    private final TradingProperties tradingProperties;
    private final OperationalEventRecorder recorder;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void captureStartupState() {
        capture();
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    @Transactional
    public void detectChange() {
        capture();
    }

    private void capture() {
        boolean current = tradingProperties.killSwitch() == null
                || tradingProperties.killSwitch().enabled();
        BrokerRuntimeStateJpaEntity state = repository.findById(STATE_KEY).orElse(null);
        if (state == null) {
            repository.save(new BrokerRuntimeStateJpaEntity(
                    STATE_KEY, Boolean.toString(current), clock.instant()));
            return;
        }
        boolean previous = Boolean.parseBoolean(state.getStateValue());
        if (previous != current) {
            recorder.recordKillSwitchChange(previous, current);
            state.update(Boolean.toString(current), clock.instant());
            repository.save(state);
        }
    }
}
