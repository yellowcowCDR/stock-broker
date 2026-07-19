package com.hermes.broker.trading.dto;

import java.math.BigDecimal;

public record RiskPolicyResponseDto(
        String version,
        BigDecimal dailyMaxLossRate,
        BigDecimal maxOrderAmount,
        int maxDailyTrades,
        int maxPositionCount,
        BigDecimal maxSectorExposureRate,
        BigDecimal maxStockExposureRate,
        BigDecimal maxPriceDeviationRate,
        boolean allowAveragingDown,
        boolean allowMarginTrading,
        boolean liveTradingEnabled,
        boolean requireSectorData,
        boolean requireDailyLossData,
        BigDecimal overseasMaxOrderAmountUsd,
        int overseasMaxDailyTrades,
        int overseasMaxPositionCount,
        BigDecimal overseasMaxStockExposureRate,
        boolean overseasAllowAveragingDown,
        boolean overseasDailyLossLimitAvailable,
        boolean overseasLiveTradingEnabled
) {
}
