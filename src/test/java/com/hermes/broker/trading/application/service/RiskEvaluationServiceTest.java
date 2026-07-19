package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.property.OverseasRiskPolicyProperties;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.trading.application.port.in.GetPortfolioSummaryUseCase;
import com.hermes.broker.trading.application.port.in.OrderRiskCommand;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.application.port.out.LoadOverseasAccountDataPort;
import com.hermes.broker.market.application.service.StockSectorResolver;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import com.hermes.broker.trading.domain.risk.RiskDecision;
import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskEvaluationServiceTest {

    private final GetPortfolioSummaryUseCase portfolioUseCase = mock(GetPortfolioSummaryUseCase.class);
    private final TradingLogRepository tradingLogRepository = mock(TradingLogRepository.class);
    private final LoadOverseasAccountDataPort overseasAccountDataPort = mock(LoadOverseasAccountDataPort.class);
    private final StockSectorResolver stockSectorResolver = mock(StockSectorResolver.class);
    private final RiskEvaluationService service = new RiskEvaluationService(
            policy(), portfolioUseCase, tradingLogRepository, new TradingTimeService(Clock.systemUTC()),
            overseasAccountDataPort,
            new OverseasRiskPolicyProperties(
                    new BigDecimal("1000"), 5, 5, new BigDecimal("0.25"), false),
            stockSectorResolver);

    @Test
    void incompleteDailyLossDataBlocksBuy() {
        when(portfolioUseCase.getPortfolioSummary()).thenReturn(portfolio(false, true, null, List.of()));

        RiskEvaluationResult result = service.evaluate(command(OrderType.BUY, BigDecimal.ONE, "SEMICONDUCTOR"));

        assertFalse(result.allowed());
        assertEquals(RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, result.decision());
    }

    @Test
    void incompleteSectorDataBlocksBuy() {
        when(portfolioUseCase.getPortfolioSummary()).thenReturn(
                portfolio(true, false, BigDecimal.ZERO, List.of()));

        RiskEvaluationResult result = service.evaluate(command(OrderType.BUY, BigDecimal.ONE, "SEMICONDUCTOR"));

        assertFalse(result.allowed());
        assertEquals(RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, result.decision());
    }

    @Test
    void negativeKisAssetChangeRateAboveLimitBlocksBuy() {
        when(portfolioUseCase.getPortfolioSummary()).thenReturn(
                portfolio(true, true, new BigDecimal("-0.031"), List.of()));

        RiskEvaluationResult result = service.evaluate(command(OrderType.BUY, BigDecimal.ONE, "SEMICONDUCTOR"));

        assertFalse(result.allowed());
        assertEquals(RiskDecision.BLOCKED_BY_DAILY_LOSS, result.decision());
    }

    @Test
    void marketRiskMultiplierReducesMaximumOrderAmount() {
        when(portfolioUseCase.getPortfolioSummary()).thenReturn(
                portfolio(true, true, BigDecimal.ZERO, List.of()));

        OrderRiskCommand command = new OrderRiskCommand(
                "account-key", "005930", null, MarketType.DOMESTIC, OrderType.BUY,
                new BigDecimal("70000"), BigDecimal.ONE, "SEMICONDUCTOR",
                "context-1", new BigDecimal("0.05"));
        RiskEvaluationResult result = service.evaluate(command);

        assertFalse(result.allowed());
        assertEquals(RiskDecision.BLOCKED_BY_MAX_ORDER_AMOUNT, result.decision());
        assertEquals(new BigDecimal("50000.00"), result.snapshot().get("effectiveMaxOrderAmount"));
    }

    @Test
    void sellAboveAvailableQuantityIsBlocked() {
        PortfolioPosition position = new PortfolioPosition(
                "005930", "Samsung", MarketType.DOMESTIC, "SEMICONDUCTOR",
                new BigDecimal("10"), new BigDecimal("3"), new BigDecimal("65000"),
                new BigDecimal("70000"), new BigDecimal("700000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("0.7"));
        when(portfolioUseCase.getPortfolioSummary()).thenReturn(
                portfolio(true, true, BigDecimal.ZERO, List.of(position)));

        RiskEvaluationResult result = service.evaluate(command(OrderType.SELL, new BigDecimal("4"), "SEMICONDUCTOR"));

        assertFalse(result.allowed());
        assertEquals(RiskDecision.BLOCKED_BY_INSUFFICIENT_POSITION, result.decision());
    }

    @Test
    void completeUsdAccountAndSymbolCapacityAllowOverseasPaperBuy() {
        Instant now = Instant.parse("2026-07-19T12:00:00Z");
        when(overseasAccountDataPort.loadUnitedStatesAccount()).thenReturn(
                new OverseasAccountSnapshot(
                        "840", "USD", new BigDecimal("5000"), new BigDecimal("4500"),
                        List.of(), "KIS_OPEN_API:PRESENT_BALANCE", now, true));
        when(overseasAccountDataPort.loadOrderCapacity("AAPL", "NASD", new BigDecimal("200")))
                .thenReturn(new OverseasOrderCapacity(
                        "AAPL", "NASD", "USD", new BigDecimal("200"),
                        new BigDecimal("4500"), new BigDecimal("4500"),
                        new BigDecimal("22"), new BigDecimal("22"),
                        new BigDecimal("1380"), "KIS_OPEN_API:PSAMOUNT", now, true));

        RiskEvaluationResult result = service.evaluate(new OrderRiskCommand(
                "account-key", "AAPL", "NASD", MarketType.OVERSEAS, OrderType.BUY,
                new BigDecimal("200"), BigDecimal.ONE, "TECHNOLOGY",
                "context-us", BigDecimal.ONE));

        assertEquals(RiskDecision.ALLOWED, result.decision());
        assertEquals("USD", result.snapshot().get("currency"));
        assertEquals(new BigDecimal("200"), result.snapshot().get("orderAmountUsd"));
    }

    @Test
    void overseasSellUsesRealExchangeSpecificSellableQuantity() {
        Instant now = Instant.parse("2026-07-19T12:00:00Z");
        var position = new com.hermes.broker.trading.domain.portfolio.OverseasPosition(
                "AAPL", "NASD", "USD", new BigDecimal("10"), new BigDecimal("2"),
                new BigDecimal("180"), new BigDecimal("200"), new BigDecimal("2000"),
                new BigDecimal("200"), new BigDecimal("0.10"));
        when(overseasAccountDataPort.loadUnitedStatesAccount()).thenReturn(
                new OverseasAccountSnapshot(
                        "840", "USD", new BigDecimal("500"), new BigDecimal("500"),
                        List.of(position), "KIS_OPEN_API:PRESENT_BALANCE", now, true));

        RiskEvaluationResult result = service.evaluate(new OrderRiskCommand(
                "account-key", "AAPL", "NASD", MarketType.OVERSEAS, OrderType.SELL,
                new BigDecimal("200"), new BigDecimal("3"), "NOT_REQUIRED_FOR_SELL",
                null, BigDecimal.ONE));

        assertFalse(result.allowed());
        assertEquals(RiskDecision.BLOCKED_BY_INSUFFICIENT_POSITION, result.decision());
    }

    private OrderRiskCommand command(OrderType orderType, BigDecimal quantity, String sector) {
        return new OrderRiskCommand(
                "account-key", "005930", null, MarketType.DOMESTIC, orderType,
                new BigDecimal("70000"), quantity, sector, "context-1", BigDecimal.ONE);
    }

    private PortfolioSummary portfolio(
            boolean dailyLossComplete,
            boolean sectorDataComplete,
            BigDecimal dailyAssetChangeRate,
            List<PortfolioPosition> positions) {
        return new PortfolioSummary(
                new BigDecimal("1000000"),
                new BigDecimal("500000"),
                new BigDecimal("500000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000000"),
                BigDecimal.ZERO,
                new BigDecimal("1000000"),
                dailyLossComplete ? BigDecimal.ZERO : null,
                dailyAssetChangeRate,
                dailyLossComplete,
                dailyLossComplete ? "KIS_OPEN_API:INQUIRE_BALANCE:ASST_ICDC" : null,
                new BigDecimal("0.5"),
                positions.size(),
                positions,
                List.of(),
                sectorDataComplete,
                sectorDataComplete ? "KIS_OPEN_API:SEARCH_STOCK_INFO" : null,
                Instant.now()
        );
    }

    private RiskPolicyProperties policy() {
        return new RiskPolicyProperties(
                "risk-v1",
                new BigDecimal("0.03"),
                new BigDecimal("1000000"),
                5,
                5,
                new BigDecimal("0.40"),
                new BigDecimal("0.25"),
                new BigDecimal("0.10"),
                false,
                false,
                false,
                true,
                true
        );
    }
}
