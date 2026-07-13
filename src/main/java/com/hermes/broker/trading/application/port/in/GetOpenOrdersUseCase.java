package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import java.util.List;

public interface GetOpenOrdersUseCase {
    List<OpenOrder> getOpenOrders();
}
