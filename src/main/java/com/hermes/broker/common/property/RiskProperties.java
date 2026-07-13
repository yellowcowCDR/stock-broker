package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.risk")
public record RiskProperties(
        boolean liveTradingEnabled,
        BigDecimal dailyMaxLossRate,
        BigDecimal maxOrderAmount,
        int maxDailyTrades,
        int maxPositionCount,
        boolean allowAveragingDown
) {
}
