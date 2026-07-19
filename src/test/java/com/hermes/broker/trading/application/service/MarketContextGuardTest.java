package com.hermes.broker.trading.application.service;

import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketContextGuardTest {

    private static final Instant NOW = Instant.parse("2026-07-20T01:00:00Z");
    private final LoadMarketContextPort port = mock(LoadMarketContextPort.class);
    private final MarketContextGuard guard = new MarketContextGuard(
            port, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void missingContextBlocksEntry() {
        when(port.loadLatest(MarketType.DOMESTIC)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.validateEntry(MarketType.DOMESTIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No market context");
    }

    @Test
    void staleContextBlocksEntry() {
        when(port.loadLatest(MarketType.DOMESTIC)).thenReturn(Optional.of(
                context(MarketEntryPolicy.ALLOW_NEW_ENTRIES, BigDecimal.ONE, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> guard.validateEntry(MarketType.DOMESTIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale");
    }

    @Test
    void blockedEntryPolicyBlocksEvenWithFreshOverview() {
        when(port.loadLatest(MarketType.DOMESTIC)).thenReturn(Optional.of(
                context(MarketEntryPolicy.BLOCK_NEW_ENTRIES, BigDecimal.ZERO, NOW.plusSeconds(60))));

        assertThatThrownBy(() -> guard.validateEntry(MarketType.DOMESTIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blocks new entries");
    }

    @Test
    void freshReducingContextIsAllowedAndReturned() {
        MarketContext expected = context(
                MarketEntryPolicy.REDUCE_NEW_ENTRIES, new BigDecimal("0.4"), NOW.plusSeconds(60));
        when(port.loadLatest(MarketType.DOMESTIC)).thenReturn(Optional.of(expected));

        MarketContext result = guard.validateEntry(MarketType.DOMESTIC);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void changedContextBlocksStaleDecisionLinkage() {
        when(port.loadLatest(MarketType.DOMESTIC)).thenReturn(Optional.of(
                context(MarketEntryPolicy.ALLOW_NEW_ENTRIES,
                        BigDecimal.ONE, NOW.plusSeconds(60))));

        assertThatThrownBy(() -> guard.validateEntry(MarketType.DOMESTIC, "older-context"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fresh Feature and Decision");
    }

    private MarketContext context(
            MarketEntryPolicy policy, BigDecimal multiplier, Instant contextValidUntil) {
        MarketOverview overview = new MarketOverview(
                MarketType.DOMESTIC, List.of(), 1, 0, 0, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "KIS_API_NATIVE", "KIS_OPEN_API:TEST", NOW.minusSeconds(10),
                NOW.plusSeconds(120), true, "FRESH");
        return new MarketContext(
                "context-1", MarketType.DOMESTIC, policy, multiplier, overview,
                List.of("test"), "hermes", "correlation-1", NOW.minusSeconds(10),
                contextValidUntil);
    }
}
