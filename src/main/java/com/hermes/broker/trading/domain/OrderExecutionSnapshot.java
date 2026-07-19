package com.hermes.broker.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderExecutionSnapshot(
        String orderId,
        String originalOrderId,
        String stockCode,
        String exchangeCode,
        OrderType orderType,
        BigDecimal orderedQuantity,
        BigDecimal executedQuantity,
        BigDecimal executionPrice,
        BigDecimal remainingQuantity,
        boolean canceled,
        boolean rejected,
        String message,
        Instant fetchedAt
) {
}
