package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.OperationalMonitoringProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationalStatusService {

    private final OperationalMonitoringProperties properties;
    private final TradingProperties tradingProperties;
    private final OperationalEventRecorder recorder;
    private final CronHeartbeatService heartbeatService;
    private final LoadMarketContextPort loadMarketContextPort;
    private final MarketTimeValidator marketTimeValidator;
    private final Clock clock;

    public OperationalStatus getStatus() {
        Instant now = clock.instant();
        Map<String, SourceObservation> sources = recorder.sourceSnapshot();
        List<OperationalAlert> alerts = new ArrayList<>();
        if (properties.enabled()) {
            addSourceAlerts(alerts, sources, now);
            addEventAlerts(alerts, recorder.eventSnapshot(), now);
            addMarketContextAlert(alerts, now);
        }

        List<CronHeartbeat> heartbeats;
        try {
            heartbeats = heartbeatService.loadAll();
            if (properties.enabled()) {
                addCronAlerts(alerts, heartbeats, now);
            }
        } catch (RuntimeException databaseFailure) {
            heartbeats = List.of();
            alerts.add(alert(
                    "CRON_HEARTBEAT_DB_UNAVAILABLE", AlertSeverity.CRITICAL,
                    "Cron heartbeat state could not be loaded from Broker DB.", now,
                    Map.of("reason", safeMessage(databaseFailure))));
        }

        recorder.updateAlerts(alerts);
        boolean killSwitch = tradingProperties.killSwitch() == null
                || tradingProperties.killSwitch().enabled();
        return new OperationalStatus(
                now, killSwitch, List.copyOf(alerts), sources, heartbeats);
    }

    private void addSourceAlerts(List<OperationalAlert> alerts,
                                 Map<String, SourceObservation> sources, Instant now) {
        sources.forEach((source, observation) -> {
            Duration maxAge = maxAge(source);
            boolean explicitlyStale = "STALE".equalsIgnoreCase(observation.freshness());
            if (explicitlyStale || (maxAge != null && observation.dataFetchedAt() != null
                    && observation.dataFetchedAt().plus(maxAge).isBefore(now))) {
                alerts.add(alert(
                        "DATA_STALE_" + code(source), AlertSeverity.WARNING,
                        source + " data is stale.", now,
                        Map.of("fetchedAt", value(observation.dataFetchedAt()),
                                "maxAgeSeconds", maxAge == null ? 0 : maxAge.toSeconds(),
                                "freshness", value(observation.freshness()))));
            }
            if (observation.dataFetchedAt() != null && !observation.complete()) {
                alerts.add(alert(
                        "DATA_INCOMPLETE_" + code(source), AlertSeverity.WARNING,
                        source + " returned incomplete data.", now,
                        Map.of("fetchedAt", observation.dataFetchedAt())));
            }
            if (observation.lastFailureAt() != null
                    && (observation.lastSuccessAt() == null
                    || observation.lastFailureAt().isAfter(observation.lastSuccessAt()))
                    && isRecent(observation.lastFailureAt(),
                    properties.externalFailureAlertWindow(), now)) {
                AlertSeverity severity = "kis".equals(source)
                        ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
                alerts.add(alert(
                        "EXTERNAL_PROVIDER_FAILURE_" + code(source), severity,
                        source + " external API is failing.", now,
                        Map.of("failedAt", observation.lastFailureAt(),
                                "reason", value(observation.lastFailureMessage()))));
            }
        });
    }

    private void addEventAlerts(List<OperationalAlert> alerts,
                                Map<String, OperationalEvent> events, Instant now) {
        OperationalEvent db = events.get("database-save");
        if (db != null && isRecent(db.occurredAt(), properties.databaseFailureAlertWindow(), now)) {
            alerts.add(alert("DATABASE_SAVE_FAILURE", AlertSeverity.CRITICAL,
                    "A Broker database mutation failed.", now,
                    Map.of("failedAt", db.occurredAt(), "reason", value(db.message()))));
        }
        OperationalEvent reconciliation = events.get("order-reconciliation");
        if (reconciliation != null && isRecent(reconciliation.occurredAt(),
                properties.reconciliationFailureAlertWindow(), now)) {
            alerts.add(alert("ORDER_RECONCILIATION_FAILURE", AlertSeverity.CRITICAL,
                    "A KIS order reconciliation failed.", now,
                    Map.of("failedAt", reconciliation.occurredAt(),
                            "reason", value(reconciliation.message()))));
        }
        OperationalEvent dailyLoss = events.get("daily-loss-limit");
        if (dailyLoss != null && isRecent(dailyLoss.occurredAt(), Duration.ofHours(24), now)) {
            alerts.add(alert("DAILY_LOSS_LIMIT_REACHED", AlertSeverity.CRITICAL,
                    "The Broker daily loss limit was reached.", now,
                    Map.of("detectedAt", dailyLoss.occurredAt(),
                            "reason", value(dailyLoss.message()))));
        }
        OperationalEvent killSwitchChange = events.get("kill-switch-change");
        if (killSwitchChange != null
                && isRecent(killSwitchChange.occurredAt(), Duration.ofHours(24), now)) {
            boolean disabled = killSwitchChange.message() != null
                    && killSwitchChange.message().endsWith("to false");
            alerts.add(alert("KILL_SWITCH_CHANGED",
                    disabled ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                    "The Broker entry kill switch changed.", now,
                    Map.of("changedAt", killSwitchChange.occurredAt(),
                            "change", value(killSwitchChange.message()))));
        }
        events.values().stream()
                .filter(event -> event.type().startsWith("scheduler:"))
                .filter(event -> isRecent(event.occurredAt(), Duration.ofHours(24), now))
                .forEach(event -> alerts.add(alert(
                        "BROKER_SCHEDULER_FAILURE", AlertSeverity.WARNING,
                        "A Broker internal scheduler failed.", now,
                        Map.of("scheduler", event.type().substring("scheduler:".length()),
                                "failedAt", event.occurredAt(),
                                "reason", value(event.message())))));
    }

    private void addMarketContextAlert(List<OperationalAlert> alerts, Instant now) {
        try {
            if (!marketTimeValidator.getMarketStatus("DOMESTIC").isOpen()) {
                return;
            }
            MarketContext context = loadMarketContextPort.loadLatest(MarketType.DOMESTIC).orElse(null);
            AlertSeverity unavailableSeverity = autonomyEnabled()
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            if (context == null) {
                alerts.add(alert("MARKET_CONTEXT_MISSING", unavailableSeverity,
                        "No domestic market context exists.", now, Map.of()));
                return;
            }
            if (context.validUntil() == null || !now.isBefore(context.validUntil())) {
                alerts.add(alert("MARKET_CONTEXT_EXPIRED", unavailableSeverity,
                        "The domestic market context has expired.", now,
                        Map.of("contextId", context.contextId(),
                                "validUntil", value(context.validUntil()))));
            } else if (!now.plus(properties.contextWarningBefore()).isBefore(context.validUntil())) {
                alerts.add(alert("MARKET_CONTEXT_EXPIRING", AlertSeverity.WARNING,
                        "The domestic market context is close to expiry.", now,
                        Map.of("contextId", context.contextId(),
                                "validUntil", context.validUntil())));
            }
        } catch (RuntimeException failure) {
            alerts.add(alert("MARKET_CONTEXT_CHECK_FAILED", AlertSeverity.CRITICAL,
                    "The latest market context could not be checked.", now,
                    Map.of("reason", safeMessage(failure))));
        }
    }

    private void addCronAlerts(List<OperationalAlert> alerts,
                               List<CronHeartbeat> heartbeats, Instant now) {
        for (CronHeartbeat heartbeat : heartbeats) {
            if (heartbeat.phase() == CronHeartbeatPhase.FAILED) {
                alerts.add(alert("CRON_FAILED", AlertSeverity.CRITICAL,
                        "Hermes Cron reported a failed execution.", now,
                        cronDetails(heartbeat)));
            }
            if (heartbeat.expectedNextAt() != null
                    && heartbeat.expectedNextAt().plus(properties.cronGrace()).isBefore(now)) {
                alerts.add(alert("CRON_MISSED", AlertSeverity.CRITICAL,
                        "Hermes Cron missed its expected heartbeat.", now,
                        cronDetails(heartbeat)));
            }
        }
    }

    private Map<String, Object> cronDetails(CronHeartbeat heartbeat) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cronName", heartbeat.cronName());
        details.put("executionId", heartbeat.executionId());
        details.put("phase", heartbeat.phase());
        details.put("expectedNextAt", heartbeat.expectedNextAt());
        if (heartbeat.message() != null) details.put("message", heartbeat.message());
        return details;
    }

    private boolean autonomyEnabled() {
        AutonomyMode mode = tradingProperties.autonomyMode();
        return mode == AutonomyMode.PAPER_AUTO || mode == AutonomyMode.LIVE_AUTO;
    }

    private Duration maxAge(String source) {
        return switch (source) {
            case "news" -> properties.newsMaxAge();
            case "watchlist" -> properties.watchlistMaxAge();
            case "overview" -> properties.overviewMaxAge();
            default -> null;
        };
    }

    private boolean isRecent(Instant eventAt, Duration window, Instant now) {
        return eventAt != null && window != null && !eventAt.plus(window).isBefore(now);
    }

    private OperationalAlert alert(String code, AlertSeverity severity, String message,
                                   Instant now, Map<String, Object> details) {
        return new OperationalAlert(code, severity, message, now, details);
    }

    private String code(String value) {
        return value.toUpperCase().replace('-', '_');
    }

    private Object value(Object value) {
        return value == null ? "unavailable" : value;
    }

    private String safeMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
