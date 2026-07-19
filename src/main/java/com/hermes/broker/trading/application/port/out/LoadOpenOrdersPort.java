package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import java.util.List;
import com.hermes.broker.trading.domain.MarketType;

public interface LoadOpenOrdersPort {
    default boolean supports(MarketType marketType) {
        return marketType == MarketType.DOMESTIC;
    }
    List<OpenOrder> loadOpenOrders();
}
