package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import java.util.List;

public interface LoadOpenOrdersPort {
    List<OpenOrder> loadOpenOrders();
}
