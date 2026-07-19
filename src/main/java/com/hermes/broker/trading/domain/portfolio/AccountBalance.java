package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;

public record AccountBalance(
        BigDecimal totalAssetAmount,
        BigDecimal cashAmount,
        BigDecimal totalEvaluationAmount,
        BigDecimal totalProfitLossAmount,
        BigDecimal previousTotalAssetAmount,
        BigDecimal dailyAssetChangeAmount,
        BigDecimal dailyAssetChangeRate,
        boolean dailyAssetChangeDataComplete,
        String dailyAssetChangeDataSource,
        BigDecimal usdCash,
        BigDecimal usdBuyingPower
) {
}
