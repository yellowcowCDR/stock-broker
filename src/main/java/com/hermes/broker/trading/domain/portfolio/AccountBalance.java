package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;

public record AccountBalance(
        BigDecimal totalAssetAmount,
        BigDecimal cashAmount,
        BigDecimal totalEvaluationAmount,
        BigDecimal totalProfitLossAmount
) {
}
