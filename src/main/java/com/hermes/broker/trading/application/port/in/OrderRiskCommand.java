package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;

import java.math.BigDecimal;

public record OrderRiskCommand(
        String stockCode,
        MarketType marketType,
        OrderType orderType,
        BigDecimal price,
        BigDecimal quantity,
        String sector
) {
}
