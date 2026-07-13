package com.hermes.broker.trading.domain.portfolio;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OpenOrder(
        String orderId,
        String stockCode,
        MarketType marketType,
        OrderType orderType,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal executedQuantity,
        LocalDateTime orderedAt
) {
}
