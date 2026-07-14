package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;

public record OverseasBalance(
        BigDecimal usdCash,
        BigDecimal usdBuyingPower
) {
}
