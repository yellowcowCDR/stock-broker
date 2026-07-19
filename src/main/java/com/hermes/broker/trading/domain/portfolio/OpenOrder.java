package com.hermes.broker.trading.domain.portfolio;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OpenOrder(
        String orderId,
        String stockCode,
        String exchangeCode,
        MarketType marketType,
        OrderType orderType,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal executedQuantity,
        Instant orderedAt
) {
    public OpenOrder(
            String orderId,
            String stockCode,
            MarketType marketType,
            OrderType orderType,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal executedQuantity,
            Instant orderedAt
    ) {
        this(orderId, stockCode, null, marketType, orderType, price, quantity, executedQuantity, orderedAt);
    }
}
