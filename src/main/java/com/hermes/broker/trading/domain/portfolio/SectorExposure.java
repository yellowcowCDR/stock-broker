package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;

public record SectorExposure(
        String sector,
        BigDecimal evaluationAmount,
        BigDecimal exposureRate
) {
}
