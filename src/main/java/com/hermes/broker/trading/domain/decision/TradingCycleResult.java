package com.hermes.broker.trading.domain.decision;

import java.time.LocalDateTime;

public record TradingCycleResult(
        String stockCode,
        boolean success,
        String message,
        TradingDecision decision,
        LocalDateTime executedAt
) {
}
