package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.MarketType;

public interface CancelOrderPort {
    void cancelOrder(String orderId, String stockCode, MarketType marketType);
}
