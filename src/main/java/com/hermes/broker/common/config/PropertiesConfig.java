package com.hermes.broker.common.config;

import com.hermes.broker.common.property.NaverNewsProperties;
import com.hermes.broker.common.property.OpenDartProperties;
import com.hermes.broker.common.property.RiskProperties;
import com.hermes.broker.common.property.TradingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        OpenDartProperties.class,
        NaverNewsProperties.class,
        TradingProperties.class,
        RiskProperties.class
})
public class PropertiesConfig {
}
