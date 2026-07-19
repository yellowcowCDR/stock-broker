package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.decision.TradingDecisionType;

import java.math.BigDecimal;

public record CreateTradingDecisionCommand(
        String featureId,
        TradingDecisionType decisionType,
        int strategyVersion,
        String reason,
        BigDecimal recommendedPrice,
        BigDecimal recommendedQuantity,
        String idempotencyKey
) {
}
