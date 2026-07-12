package com.hermes.broker.market.dto;

import java.math.BigDecimal;

public record TechnicalIndicators(
        BigDecimal ma5,
        BigDecimal ma20,
        BigDecimal ma60,
        BigDecimal rsi14
) {
}
