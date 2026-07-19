package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;

public record OverseasPosition(
        String stockCode,
        String exchangeCode,
        String currency,
        BigDecimal quantity,
        BigDecimal sellableQuantity,
        BigDecimal averagePurchasePrice,
        BigDecimal currentPrice,
        BigDecimal evaluationAmount,
        BigDecimal profitLossAmount,
        BigDecimal profitLossRate
) {
}
