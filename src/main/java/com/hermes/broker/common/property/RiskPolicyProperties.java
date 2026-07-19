package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.risk")
public record RiskPolicyProperties(
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
        boolean requireDailyLossData
) {
}
