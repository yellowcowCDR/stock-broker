package com.hermes.broker.common.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.hermes.broker.common.property.TradingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OperationalEventRecorder {

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Map<String, SourceObservation> sources = new ConcurrentHashMap<>();
    private final Map<String, OperationalEvent> events = new ConcurrentHashMap<>();
    private final AtomicInteger criticalAlerts = new AtomicInteger();
    private final AtomicInteger warningAlerts = new AtomicInteger();
    private final Map<String, AtomicInteger> alertCodes = new ConcurrentHashMap<>();

    public OperationalEventRecorder(MeterRegistry meterRegistry, Clock clock,
                                    TradingProperties tradingProperties) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        Gauge.builder("broker.kill.switch.enabled", () ->
                        tradingProperties.killSwitch() == null
                                || tradingProperties.killSwitch().enabled() ? 1 : 0)
                .description("Whether the Broker entry kill switch is enabled")
                .register(meterRegistry);
        Gauge.builder("broker.operational.alerts", criticalAlerts, AtomicInteger::get)
                .tag("severity", "critical")
                .register(meterRegistry);
        Gauge.builder("broker.operational.alerts", warningAlerts, AtomicInteger::get)
                .tag("severity", "warning")
                .register(meterRegistry);
    }

    public void recordExternalSuccess(String provider) {
        Instant now = clock.instant();
        sources.compute(provider, (key, previous) -> new SourceObservation(
                key,
                now,
                previous == null ? null : previous.dataFetchedAt(),
                previous == null || previous.complete(),
                previous == null ? null : previous.freshness(),
                previous == null ? null : previous.lastFailureAt(),
                previous == null ? null : previous.lastFailureMessage()));
        meterRegistry.counter("broker.external.calls", "provider", provider, "result", "success")
                .increment();
    }

    public void recordExternalFailure(String provider, Throwable failure) {
        Instant now = clock.instant();
        String message = safeMessage(failure);
        sources.compute(provider, (key, previous) -> new SourceObservation(
                key,
                previous == null ? null : previous.lastSuccessAt(),
                previous == null ? null : previous.dataFetchedAt(),
                previous != null && previous.complete(),
                previous == null ? null : previous.freshness(),
                now,
                message));
        meterRegistry.counter("broker.external.calls", "provider", provider, "result", "failure")
                .increment();
    }

    public void recordDataSnapshot(String source, Instant fetchedAt, boolean complete) {
        recordDataSnapshot(source, fetchedAt, complete, null);
    }

    public void recordDataSnapshot(String source, Instant fetchedAt, boolean complete,
                                   String freshness) {
        Instant now = clock.instant();
        sources.compute(source, (key, previous) -> new SourceObservation(
                key,
                now,
                fetchedAt,
                complete,
                freshness,
                previous == null ? null : previous.lastFailureAt(),
                previous == null ? null : previous.lastFailureMessage()));
        meterRegistry.counter("broker.data.snapshots", "source", source,
                        "complete", Boolean.toString(complete))
                .increment();
    }

    public void recordDatabaseFailure(String operation, Throwable failure) {
        recordEvent("database-save", operation + ": " + safeMessage(failure));
        meterRegistry.counter("broker.database.operations", "operation", operation, "result", "failure")
                .increment();
    }

    public void recordReconciliation(boolean success, String message) {
        meterRegistry.counter("broker.order.reconciliation", "result", success ? "success" : "failure")
                .increment();
        if (!success) {
            recordEvent("order-reconciliation", message);
        }
    }

    public void recordDailyLossLimit(String message) {
        recordEvent("daily-loss-limit", message);
        meterRegistry.counter("broker.risk.daily.loss.limit", "result", "reached").increment();
    }

    public void recordKillSwitchChange(boolean previous, boolean current) {
        recordEvent("kill-switch-change",
                "entry kill switch changed from " + previous + " to " + current);
        meterRegistry.counter("broker.kill.switch.changes",
                "previous", Boolean.toString(previous),
                "current", Boolean.toString(current)).increment();
    }

    public void recordScheduledExecution(String scheduler, boolean success, String message) {
        meterRegistry.counter("broker.scheduler.executions", "scheduler", scheduler,
                "result", success ? "success" : "failure").increment();
        if (!success) {
            recordEvent("scheduler:" + scheduler, message);
        } else {
            events.remove("scheduler:" + scheduler);
        }
    }

    public Map<String, SourceObservation> sourceSnapshot() {
        return Map.copyOf(sources);
    }

    public Map<String, OperationalEvent> eventSnapshot() {
        return Map.copyOf(events);
    }

    public void updateAlerts(java.util.List<OperationalAlert> alerts) {
        int critical = (int) alerts.stream()
                .filter(alert -> alert.severity() == AlertSeverity.CRITICAL).count();
        criticalAlerts.set(critical);
        warningAlerts.set(alerts.size() - critical);
        alertCodes.values().forEach(value -> value.set(0));
        for (OperationalAlert alert : alerts) {
            String key = alert.code() + "|" + alert.severity().name().toLowerCase();
            AtomicInteger value = alertCodes.computeIfAbsent(key, ignored -> {
                AtomicInteger created = new AtomicInteger();
                Gauge.builder("broker.operational.alert.active", created, AtomicInteger::get)
                        .tag("code", alert.code())
                        .tag("severity", alert.severity().name().toLowerCase())
                        .register(meterRegistry);
                return created;
            });
            value.set(1);
        }
    }

    private void recordEvent(String type, String message) {
        events.put(type, new OperationalEvent(type, clock.instant(), message));
    }

    private String safeMessage(Throwable failure) {
        if (failure == null) {
            return "unknown failure";
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
