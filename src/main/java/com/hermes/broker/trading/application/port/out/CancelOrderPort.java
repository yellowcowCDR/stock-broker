package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.MarketType;

public interface CancelOrderPort {
    boolean supports(MarketType marketType);
    void cancelOrder(String orderId, String stockCode, MarketType marketType);

    default void cancelOrder(
            String orderId,
            String stockCode,
            MarketType marketType,
            String exchangeCode,
            int remainingQuantity
    ) {
        cancelOrder(orderId, stockCode, marketType);
    }
}
