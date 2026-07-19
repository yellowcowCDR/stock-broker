package com.hermes.broker.summary.domain;

import java.math.BigDecimal;
import java.time.Instant;
import com.hermes.broker.trading.domain.MarketType;

public record TradeReview(
        String stockCode,
        MarketType marketType,
        String decisionId,
        String featureId,
        String marketContextId,
        String strategyVersion,
        String decisionType,
        String orderStatus,
        BigDecimal expectedPrice,
        BigDecimal executionPrice,
        BigDecimal orderedQuantity,
        BigDecimal executedQuantity,
        BigDecimal transactionCost,
        String costCurrency,
        String costSource,
        BigDecimal slippageAmount,
        Instant featureSnapshotAt,
        Instant decidedAt,
        Instant orderCreatedAt,
        boolean dataComplete,
        String reason
) {
}
