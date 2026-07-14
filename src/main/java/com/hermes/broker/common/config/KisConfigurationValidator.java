package com.hermes.broker.common.config;

import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.TradingProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisConfigurationValidator {

    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;
    private final Environment environment;

    @PostConstruct
    public void validate() {
        log.info("Validating KIS Configuration...");
        validateProfileAndEnvironment();
        validateBaseUrl();
        validateRateLimit();
        validateTradingMode();
        log.info("KIS Configuration validation passed.");
    }

    private void validateProfileAndEnvironment() {
        boolean isMockProfile = Arrays.asList(environment.getActiveProfiles()).contains("mock");
        boolean isProdProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (isMockProfile && kisProperties.environment() != KisEnvironment.MOCK) {
            throw new IllegalStateException("Profile is 'mock' but KisEnvironment is " + kisProperties.environment());
        }
        if (isProdProfile && kisProperties.environment() != KisEnvironment.PRODUCTION) {
            throw new IllegalStateException("Profile is 'prod' but KisEnvironment is " + kisProperties.environment());
        }
    }

    private void validateBaseUrl() {
        String baseUrl = kisProperties.baseUrl();
        if (baseUrl == null) {
            throw new IllegalStateException("KIS baseUrl is not configured.");
        }

        if (kisProperties.environment() == KisEnvironment.MOCK && !baseUrl.contains("openapivts")) {
            throw new IllegalStateException("MOCK environment must use openapivts base URL. Found: " + baseUrl);
        }
        if (kisProperties.environment() == KisEnvironment.PRODUCTION && baseUrl.contains("openapivts")) {
            throw new IllegalStateException("PRODUCTION environment must NOT use openapivts base URL. Found: " + baseUrl);
        }
    }

    private void validateRateLimit() {
        if (!kisProperties.rateLimit().enabled()) {
            return;
        }

        Duration minInterval = kisProperties.rateLimit().minimumInterval();
        int rps = kisProperties.rateLimit().requestsPerSecond();

        if (minInterval != null && rps > 0) {
            long theoreticalMin = (long) Math.ceil(1000.0 / rps);
            if (minInterval.toMillis() < theoreticalMin) {
                throw new IllegalStateException(String.format(
                        "minimumInterval (%d ms) is shorter than allowed by requestsPerSecond (%d -> %d ms).",
                        minInterval.toMillis(), rps, theoreticalMin));
            }
        }

        if (kisProperties.environment() == KisEnvironment.MOCK) {
            if (minInterval == null || minInterval.toMillis() < 1000) {
                throw new IllegalStateException("MOCK environment requires minimumInterval >= 1000ms");
            }
        }
    }

    private void validateTradingMode() {
        boolean realOrderEnabled = tradingProperties.realOrder() != null && tradingProperties.realOrder().enabled();
        boolean killSwitchEnabled = tradingProperties.killSwitch() == null || tradingProperties.killSwitch().enabled();

        if (kisProperties.environment() == KisEnvironment.MOCK) {
            if (realOrderEnabled) {
                throw new IllegalStateException("Real orders cannot be enabled in MOCK environment.");
            }
        } else if (kisProperties.environment() == KisEnvironment.PRODUCTION) {
            if (kisProperties.api().accountNo() == null || kisProperties.api().accountNo().isBlank()) {
                throw new IllegalStateException("PRODUCTION environment requires an account number.");
            }
            if (kisProperties.api().appKey() == null || kisProperties.api().appKey().isBlank()) {
                throw new IllegalStateException("PRODUCTION environment requires an app key.");
            }
            if (realOrderEnabled && killSwitchEnabled) {
                log.warn("Real orders are enabled but kill switch is also active. Orders will be blocked.");
            }
        }
    }
}
