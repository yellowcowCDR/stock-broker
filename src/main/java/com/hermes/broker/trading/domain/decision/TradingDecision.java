package com.hermes.broker.trading.domain.decision;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingDecision(
        String decisionId,
        String featureId,
        String stockCode,
        TradingDecisionType decisionType,
        String strategyVersion,
        String reason,
        BigDecimal recommendedPrice,
        BigDecimal recommendedQuantity,
        Instant decidedAt,
        TradingDecisionMode mode,
        String idempotencyKey
) {
    public TradingDecision(
            String decisionId,
            String featureId,
            String stockCode,
            TradingDecisionType decisionType,
            String strategyVersion,
            String reason,
            BigDecimal recommendedPrice,
            BigDecimal recommendedQuantity,
            Instant decidedAt
    ) {
        this(decisionId, featureId, stockCode, decisionType, strategyVersion,
                reason, recommendedPrice, recommendedQuantity, decidedAt,
                TradingDecisionMode.ACTIVE, null);
    }

    public TradingDecision {
        mode = mode == null ? TradingDecisionMode.ACTIVE : mode;
    }
}
