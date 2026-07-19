package com.hermes.broker.agent.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record AgentSkillPerformance(
        String skillVersion,
        int tradeCount,
        int evaluationDays,
        BigDecimal winRate,
        BigDecimal totalReturnRate,
        BigDecimal averageReturnRate,
        BigDecimal averageProfit,
        BigDecimal averageLoss,
        BigDecimal profitLossRatio,
        BigDecimal profitFactor,
        BigDecimal sharpeRatio,
        BigDecimal maxDrawdown,
        BigDecimal holdAccuracy,
        BigDecimal riskBlockEffect,
        Instant evaluatedAt
) {
}
