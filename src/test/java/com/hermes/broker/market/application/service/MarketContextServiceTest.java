package com.hermes.broker.market.application.service;

import com.hermes.broker.common.property.MarketContextProperties;
import com.hermes.broker.market.application.port.in.CreateMarketContextCommand;
import com.hermes.broker.market.application.port.in.GetMarketOverviewUseCase;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.application.port.out.SaveMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketContextServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T01:00:00Z");

    @Mock GetMarketOverviewUseCase getMarketOverviewUseCase;
    @Mock SaveMarketContextPort saveMarketContextPort;
    @Mock LoadMarketContextPort loadMarketContextPort;

    private MarketContextService service;

    @BeforeEach
    void setUp() {
        service = new MarketContextService(
                getMarketOverviewUseCase,
                saveMarketContextPort,
                loadMarketContextPort,
                new MarketContextProperties(Duration.ofMinutes(5), Duration.ofMinutes(5), 100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesServerFetchedOverviewWithBoundedValidity() {
        when(getMarketOverviewUseCase.getOverview(MarketType.DOMESTIC)).thenReturn(overview());
        when(saveMarketContextPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MarketContext result = service.create(command(null));

        assertThat(result.contextId()).isNotBlank();
        assertThat(result.overviewSnapshot().dataSource()).isEqualTo("KIS_OPEN_API:TEST");
        assertThat(result.validUntil()).isEqualTo(NOW.plusSeconds(300));
        assertThat(result.riskMultiplier()).isEqualByComparingTo("0.5");
        assertThat(result.correlationId()).isNotBlank();
    }

    @Test
    void requestedValidityBeyondOverviewIsRejected() {
        when(getMarketOverviewUseCase.getOverview(MarketType.DOMESTIC)).thenReturn(overview());

        assertThatThrownBy(() -> service.create(command(NOW.plusSeconds(301))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("freshness");
    }

    @Test
    void blockPolicyCannotCarryPositiveRiskMultiplier() {
        CreateMarketContextCommand invalid = new CreateMarketContextCommand(
                MarketType.DOMESTIC,
                MarketEntryPolicy.BLOCK_NEW_ENTRIES,
                new BigDecimal("0.5"),
                null,
                List.of("risk off"),
                "hermes",
                null);

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("riskMultiplier 0");
    }

    private CreateMarketContextCommand command(Instant validUntil) {
        return new CreateMarketContextCommand(
                MarketType.DOMESTIC,
                MarketEntryPolicy.REDUCE_NEW_ENTRIES,
                new BigDecimal("0.5"),
                validUntil,
                List.of("breadth is mixed"),
                "hermes-cron",
                null);
    }

    private MarketOverview overview() {
        return new MarketOverview(
                MarketType.DOMESTIC, List.of(), 600, 300, 100, new BigDecimal("0.3"),
                new BigDecimal("120000"), new BigDecimal("-200000"), new BigDecimal("80000"),
                "KIS_API_NATIVE", "KIS_OPEN_API:TEST", NOW, NOW.plusSeconds(300),
                true, "FRESH");
    }
}
