package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

public record OverseasOrderCapacity(
        String stockCode,
        String exchangeCode,
        String currency,
        BigDecimal requestedPrice,
        BigDecimal orderableForeignAmount,
        BigDecimal overseasOrderableAmount,
        BigDecimal maximumOrderableQuantity,
        BigDecimal orderableQuantity,
        BigDecimal exchangeRate,
        String dataSource,
        Instant fetchedAt,
        boolean complete
) {
}
