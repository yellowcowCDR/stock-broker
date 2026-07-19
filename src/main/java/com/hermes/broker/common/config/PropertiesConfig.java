package com.hermes.broker.common.config;

import com.hermes.broker.common.property.NaverNewsProperties;
import com.hermes.broker.common.property.OpenDartProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.property.StrategyEvaluationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        OpenDartProperties.class,
        NaverNewsProperties.class,
        TradingProperties.class,
        RiskPolicyProperties.class,
        com.hermes.broker.common.property.OverseasRiskPolicyProperties.class,
        StrategyEvaluationProperties.class,
        com.hermes.broker.common.property.MarketContextProperties.class,
        com.hermes.broker.common.property.MarketWatchlistProperties.class,
        com.hermes.broker.common.property.AlphaVantageProperties.class,
        com.hermes.broker.common.property.OperationalMonitoringProperties.class,
        com.hermes.broker.common.property.KisProperties.class
})
public class PropertiesConfig {
}
