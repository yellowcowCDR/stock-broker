package com.hermes.broker.agent.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentSkillPerformance(
        String skillVersion,
        BigDecimal winRate,
        BigDecimal profitFactor,
        BigDecimal maxDrawdown,
        BigDecimal totalReturnRate,
        int tradeCount,
        LocalDateTime evaluatedAt
) {
}
