package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.RiskPolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class OrderPriceValidator {

    private final RiskPolicyProperties properties;

    public void validate(BigDecimal requestedPrice, BigDecimal currentPrice) {
        if (requestedPrice == null || requestedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Order price must be greater than zero.");
        }
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("A valid current price is required before order submission.");
        }

        BigDecimal deviationRate = requestedPrice.subtract(currentPrice).abs()
                .divide(currentPrice, 8, RoundingMode.HALF_UP);
        if (deviationRate.compareTo(properties.maxPriceDeviationRate()) > 0) {
            throw new IllegalStateException("Order price deviation exceeds policy: " + deviationRate);
        }
    }
}
