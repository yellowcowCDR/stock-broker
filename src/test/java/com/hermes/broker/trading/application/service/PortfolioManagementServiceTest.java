package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.application.service.StockSectorResolver;
import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.trading.application.port.out.LoadAccountBalancePort;
import com.hermes.broker.trading.application.port.out.LoadBuyingPowerPort;
import com.hermes.broker.trading.application.port.out.LoadPortfolioPositionsPort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.portfolio.AccountBalance;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    @Mock LoadAccountBalancePort loadAccountBalancePort;
    @Mock LoadPortfolioPositionsPort loadPortfolioPositionsPort;
    @Mock LoadBuyingPowerPort loadBuyingPowerPort;
    @Mock StockSectorResolver stockSectorResolver;

    private PortfolioManagementService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioManagementService(
                loadAccountBalancePort,
                loadPortfolioPositionsPort,
                loadBuyingPowerPort,
                Clock.fixed(NOW, ZoneOffset.UTC),
                stockSectorResolver);

        when(loadAccountBalancePort.loadBalance()).thenReturn(new AccountBalance(
                new BigDecimal("1000000"),
                new BigDecimal("300000"),
                new BigDecimal("700000"),
                new BigDecimal("50000"),
                new BigDecimal("1010000"),
                new BigDecimal("-10000"),
                new BigDecimal("-0.0099"),
                true,
                "KIS_OPEN_API:INQUIRE_BALANCE:ASST_ICDC",
                null,
                null));
        when(loadBuyingPowerPort.loadBuyingPower()).thenReturn(new BigDecimal("300000"));
        when(loadPortfolioPositionsPort.loadPositions()).thenReturn(List.of(position()));
    }

    @Test
    void enrichesRealSectorAndCalculatesActualPortfolioWeight() {
        when(stockSectorResolver.resolve("005930", MarketType.DOMESTIC)).thenReturn(new StockSector(
                "005930", MarketType.DOMESTIC, "013", "전기전자",
                "INDEX_INDUSTRY_MEDIUM", "KIS_OPEN_API:SEARCH_STOCK_INFO", NOW, true));

        PortfolioSummary result = service.getPortfolioSummary();

        assertThat(result.positions().get(0).sector()).isEqualTo("전기전자");
        assertThat(result.positions().get(0).portfolioWeight()).isEqualByComparingTo("0.7000");
        assertThat(result.sectorExposures().get(0).exposureRate()).isEqualByComparingTo("0.7000");
        assertThat(result.sectorDataComplete()).isTrue();
        assertThat(result.dailyAssetChangeRate()).isEqualByComparingTo("-0.0099");
        assertThat(result.dailyAssetChangeDataComplete()).isTrue();
    }

    @Test
    void unavailableSectorMarksPortfolioIncompleteWithoutInventingAValue() {
        when(stockSectorResolver.resolve("005930", MarketType.DOMESTIC))
                .thenThrow(new MarketDataUnavailableException("unavailable"));

        PortfolioSummary result = service.getPortfolioSummary();

        assertThat(result.positions().get(0).sector()).isEqualTo("UNKNOWN");
        assertThat(result.sectorDataComplete()).isFalse();
    }

    private PortfolioPosition position() {
        return new PortfolioPosition(
                "005930", "삼성전자", MarketType.DOMESTIC, "UNKNOWN",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("65000"),
                new BigDecimal("70000"), new BigDecimal("700000"),
                new BigDecimal("50000"), new BigDecimal("0.0769"), BigDecimal.ZERO);
    }
}
