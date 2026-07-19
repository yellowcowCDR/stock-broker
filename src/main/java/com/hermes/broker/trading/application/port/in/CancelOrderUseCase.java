package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.MarketType;

public interface CancelOrderUseCase {
    void cancelOrder(String orderId, String stockCode, MarketType marketType, String idempotencyKey);
}
