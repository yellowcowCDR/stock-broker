package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.risk")
public record RiskPolicyProperties(
        BigDecimal dailyMaxLossRate,
        BigDecimal maxOrderAmount,
        int maxDailyTrades,
        int maxPositionCount,
        BigDecimal maxSectorExposureRate,
        BigDecimal maxStockExposureRate,
        boolean allowAveragingDown,
        boolean allowMarginTrading,
        boolean liveTradingEnabled,
        boolean killSwitchEnabled
) {
}
