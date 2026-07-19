package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.risk.overseas")
public record OverseasRiskPolicyProperties(
        BigDecimal maxOrderAmountUsd,
        int maxDailyTrades,
        int maxPositionCount,
        BigDecimal maxStockExposureRate,
        boolean allowAveragingDown
) {
}
