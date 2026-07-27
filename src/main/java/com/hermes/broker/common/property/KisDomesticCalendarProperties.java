package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis.calendar")
public record KisDomesticCalendarProperties(
        boolean enabled,
        String baseUrl,
        String appKey,
        String appSecret
) {
}
