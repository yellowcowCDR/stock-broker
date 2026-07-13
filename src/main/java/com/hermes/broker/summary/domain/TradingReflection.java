package com.hermes.broker.summary.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TradingReflection(
        String reflectionId,
        LocalDate tradingDate,
        String strategyVersion,
        BigDecimal dailyReturnRate,
        BigDecimal marketReturnRate,
        List<TradeReview> reviews,
        String overallFeedback,
        String improvementPlan,
        LocalDateTime createdAt
) {
}
