package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.OperationalMonitoringProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import com.hermes.broker.market.dto.response.MarketStatusResponseDto;
import com.hermes.broker.trading.domain.MarketType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OperationalStatusServiceTest {

    @Mock CronHeartbeatService heartbeatService;
    @Mock LoadMarketContextPort marketContextPort;
    @Mock MarketTimeValidator marketTimeValidator;

    private Clock clock;
    private OperationalEventRecorder recorder;
    private OperationalStatusService service;
    private final Instant now = Instant.parse("2026-07-19T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        TradingProperties trading = tradingProperties(AutonomyMode.PAPER_AUTO, false);
        recorder = new OperationalEventRecorder(new SimpleMeterRegistry(), clock, trading);
        OperationalMonitoringProperties properties = new OperationalMonitoringProperties(
                true, Duration.ofMinutes(1), Duration.ofHours(24), Duration.ofMinutes(15),
                Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofMinutes(30),
                Duration.ofMinutes(15), Duration.ofMinutes(5));
        service = new OperationalStatusService(
                properties, trading, recorder, heartbeatService, marketContextPort,
                marketTimeValidator, clock);
        when(marketTimeValidator.getMarketStatus("DOMESTIC"))
                .thenReturn(MarketStatusResponseDto.builder()
                        .marketType("DOMESTIC").isOpen(true).complete(true).build());
        lenient().when(marketContextPort.loadLatest(MarketType.DOMESTIC))
                .thenReturn(Optional.of(context(now.plusSeconds(600))));
    }

    @Test
    void reportsStaleNewsAndMissedHermesCron() {
        recorder.recordDataSnapshot("news", now.minus(Duration.ofHours(25)), true);
        when(heartbeatService.loadAll()).thenReturn(List.of(new CronHeartbeat(
                "market-analysis", "run-1", CronHeartbeatPhase.SUCCEEDED, 300,
                now.minusSeconds(700), now.minusSeconds(700), now.minusSeconds(400),
                null, now.minusSeconds(700))));

        OperationalStatus status = service.getStatus();

        assertThat(status.activeAlerts()).extracting(OperationalAlert::code)
                .contains("DATA_STALE_NEWS", "CRON_MISSED");
        assertThat(status.activeAlerts())
                .anyMatch(alert -> alert.code().equals("CRON_MISSED")
                        && alert.severity() == AlertSeverity.CRITICAL);
    }

    @Test
    void reportsContextNearExpiryWithoutInventingAReplacement() {
        when(heartbeatService.loadAll()).thenReturn(List.of());
        when(marketContextPort.loadLatest(MarketType.DOMESTIC))
                .thenReturn(Optional.of(context(now.plusSeconds(30))));

        OperationalStatus status = service.getStatus();

        assertThat(status.activeAlerts()).extracting(OperationalAlert::code)
                .contains("MARKET_CONTEXT_EXPIRING");
    }

    @Test
    void doesNotReportCronMissedBeforeTheNextActualSlot() {
        when(heartbeatService.loadAll()).thenReturn(List.of(new CronHeartbeat(
                "hourly-market-analysis", "friday-last-run", CronHeartbeatPhase.SUCCEEDED,
                Duration.ofDays(2).toSeconds(), now.minus(Duration.ofDays(1)),
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)),
                "completed", now.minus(Duration.ofDays(1)))));

        OperationalStatus status = service.getStatus();

        assertThat(status.activeAlerts()).extracting(OperationalAlert::code)
                .doesNotContain("CRON_MISSED");
    }

    private MarketContext context(Instant validUntil) {
        MarketOverview overview = new MarketOverview(
                MarketType.DOMESTIC, List.of(), 1, 1, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "KRW",
                "KIS", now.minusSeconds(30), validUntil, true, "FRESH");
        return new MarketContext(
                "context-1", MarketType.DOMESTIC, MarketEntryPolicy.ALLOW_NEW_ENTRIES,
                BigDecimal.ONE, overview, List.of(), "hermes", "corr-1",
                now.minusSeconds(30), validUntil);
    }

    private TradingProperties tradingProperties(AutonomyMode mode, boolean killSwitch) {
        return new TradingProperties(
                null, "PAPER", mode, null,
                new TradingProperties.KillSwitchProperties(killSwitch), null);
    }
}
