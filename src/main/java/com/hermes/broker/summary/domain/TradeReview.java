package com.hermes.broker.summary.domain;

import java.math.BigDecimal;

public record TradeReview(
        String stockCode,
        String decisionId,
        String decisionType, // BUY, SELL, HOLD
        BigDecimal expectedReturn,
        BigDecimal actualReturn,
        boolean success,
        String reason
) {
}
