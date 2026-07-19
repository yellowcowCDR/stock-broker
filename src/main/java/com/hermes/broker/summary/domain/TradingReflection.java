package com.hermes.broker.summary.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import com.hermes.broker.trading.domain.MarketType;

public record TradingReflection(
        String reflectionId,
        LocalDate tradingDate,
        MarketType marketType,
        String marketZoneId,
        String strategyVersion,
        BigDecimal dailyReturnRate,
        BigDecimal marketReturnRate,
        BigDecimal totalTransactionCost,
        BigDecimal totalSlippageAmount,
        int decisionCount,
        int holdCount,
        int blockCount,
        boolean dataComplete,
        List<TradeReview> reviews,
        String overallFeedback,
        String improvementPlan,
        Instant createdAt
) {
}
