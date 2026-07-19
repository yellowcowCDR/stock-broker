package com.hermes.broker.trading.domain.decision;

import java.time.Instant;

public record TradingCycleResult(
        String stockCode,
        boolean success,
        String message,
        TradingDecision decision,
        Instant executedAt
) {
}
