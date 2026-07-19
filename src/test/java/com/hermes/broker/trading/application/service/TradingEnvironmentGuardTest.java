package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.dto.OrderRequestDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradingEnvironmentGuardTest {

    private final KisProperties kisProperties = mock(KisProperties.class);
    private final RiskPolicyProperties riskPolicy = mock(RiskPolicyProperties.class);

    @Test
    void analysisOnlyBlocksEverySubmission() {
        when(kisProperties.environment()).thenReturn(KisEnvironment.MOCK);
        TradingEnvironmentGuard guard = new TradingEnvironmentGuard(
                kisProperties,
                properties(AutonomyMode.ANALYSIS_ONLY, true),
                riskPolicy
        );

        assertThrows(IllegalStateException.class, () -> guard.validateSubmission(request(OrderType.SELL)));
    }

    @Test
    void entryKillSwitchBlocksBuyButAllowsPaperSell() {
        when(kisProperties.environment()).thenReturn(KisEnvironment.MOCK);
        TradingEnvironmentGuard guard = new TradingEnvironmentGuard(
                kisProperties,
                properties(AutonomyMode.PAPER_AUTO, true),
                riskPolicy
        );

        assertThrows(IllegalStateException.class, () -> guard.validateSubmission(request(OrderType.BUY)));
        assertDoesNotThrow(() -> guard.validateSubmission(request(OrderType.SELL)));
    }

    @Test
    void enabledOverseasOrderIsAllowedOnlyInPaperEnvironment() {
        when(kisProperties.environment()).thenReturn(KisEnvironment.MOCK);
        TradingProperties paper = new TradingProperties(
                null, "PAPER", AutonomyMode.PAPER_AUTO,
                new TradingProperties.RealOrderProperties(false),
                new TradingProperties.KillSwitchProperties(false),
                new TradingProperties.OverseasOrderProperties(true));
        TradingEnvironmentGuard paperGuard = new TradingEnvironmentGuard(
                kisProperties, paper, riskPolicy);

        assertDoesNotThrow(() -> paperGuard.validateSubmission(overseasRequest()));

        when(kisProperties.environment()).thenReturn(KisEnvironment.PRODUCTION);
        TradingProperties live = new TradingProperties(
                null, "LIVE", AutonomyMode.LIVE_AUTO,
                new TradingProperties.RealOrderProperties(true),
                new TradingProperties.KillSwitchProperties(false),
                new TradingProperties.OverseasOrderProperties(true));
        when(riskPolicy.liveTradingEnabled()).thenReturn(true);
        TradingEnvironmentGuard liveGuard = new TradingEnvironmentGuard(
                kisProperties, live, riskPolicy);

        assertThrows(IllegalStateException.class,
                () -> liveGuard.validateSubmission(overseasRequest()));
    }

    private TradingProperties properties(AutonomyMode autonomyMode, boolean killSwitch) {
        return new TradingProperties(
                new TradingProperties.SchedulerProperties(false),
                "PAPER",
                autonomyMode,
                new TradingProperties.RealOrderProperties(false),
                new TradingProperties.KillSwitchProperties(killSwitch),
                new TradingProperties.OverseasOrderProperties(false)
        );
    }

    private OrderRequestDto request(OrderType orderType) {
        return OrderRequestDto.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .orderType(orderType)
                .price(new BigDecimal("70000"))
                .quantity(1)
                .idempotencyKey("key")
                .build();
    }

    private OrderRequestDto overseasRequest() {
        return OrderRequestDto.builder()
                .marketType(MarketType.OVERSEAS)
                .stockCode("AAPL")
                .exchangeCode("NASD")
                .orderType(OrderType.BUY)
                .price(new BigDecimal("200"))
                .quantity(1)
                .idempotencyKey("overseas-key")
                .build();
    }
}
