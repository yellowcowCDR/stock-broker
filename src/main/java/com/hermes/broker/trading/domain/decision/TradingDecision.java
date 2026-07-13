package com.hermes.broker.trading.domain.decision;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradingDecision(
        String decisionId,
        String featureId,
        String stockCode,
        TradingDecisionType decisionType,
        String strategyVersion,
        String reason,
        BigDecimal recommendedPrice,
        BigDecimal recommendedQuantity,
        LocalDateTime decidedAt
) {
}
