package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.dto.OrderRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradingEnvironmentGuard {

    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;
    private final RiskPolicyProperties riskPolicyProperties;

    public void validateSubmission(OrderRequestDto request) {
        AutonomyMode autonomyMode = tradingProperties.autonomyMode() == null
                ? AutonomyMode.ANALYSIS_ONLY
                : tradingProperties.autonomyMode();

        if (autonomyMode == AutonomyMode.ANALYSIS_ONLY) {
            throw new IllegalStateException("Autonomy mode is ANALYSIS_ONLY. Order submission is blocked.");
        }

        if (request.getMarketType() == MarketType.OVERSEAS
                && (tradingProperties.overseasOrder() == null || !tradingProperties.overseasOrder().enabled())) {
            throw new IllegalStateException("Overseas orders are disabled until overseas risk data is complete.");
        }

        if (request.getOrderType() == OrderType.BUY
                && (tradingProperties.killSwitch() == null || tradingProperties.killSwitch().enabled())) {
            throw new IllegalStateException("Entry kill switch is active. New BUY orders are blocked.");
        }

        if (kisProperties.environment() == KisEnvironment.MOCK) {
            if (!"PAPER".equalsIgnoreCase(tradingProperties.mode())) {
                throw new IllegalStateException("MOCK KIS environment requires trading.mode=PAPER.");
            }
            if (autonomyMode != AutonomyMode.PAPER_AUTO) {
                throw new IllegalStateException("Paper orders require autonomy mode PAPER_AUTO.");
            }
            return;
        }

        if (request.getMarketType() == MarketType.OVERSEAS) {
            throw new IllegalStateException(
                    "Live overseas orders remain disabled; OVERSEAS_ORDER_ENABLED supports KIS Paper orders only.");
        }

        if (!"LIVE".equalsIgnoreCase(tradingProperties.mode())) {
            throw new IllegalStateException("PRODUCTION KIS environment requires trading.mode=LIVE.");
        }
        if (tradingProperties.realOrder() == null || !tradingProperties.realOrder().enabled()
                || !riskPolicyProperties.liveTradingEnabled()) {
            throw new IllegalStateException("Live order execution is disabled by Broker policy.");
        }
        if (autonomyMode != AutonomyMode.LIVE_AUTO) {
            throw new IllegalStateException("Autonomous live orders require autonomy mode LIVE_AUTO.");
        }
    }

    public void validateCancellation() {
        if (kisProperties.api() == null || kisProperties.api().accountNo() == null
                || kisProperties.api().accountNo().isBlank()) {
            throw new IllegalStateException("Broker account is not configured for cancellation.");
        }
    }
}
