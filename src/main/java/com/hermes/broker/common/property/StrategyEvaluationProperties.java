package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.strategy")
public record StrategyEvaluationProperties(
        int minimumTradeSample,
        int minimumEvaluationDays,
        int maximumParameterChangesPerUpgrade,
        boolean rollbackEnabled,
        int rollbackMinimumSample,
        BigDecimal rollbackMaxDrawdownIncreaseRate,
        BigDecimal rollbackPerformanceDegradationRate
) {
}
