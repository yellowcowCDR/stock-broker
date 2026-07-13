package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.in.CancelOrderUseCase;
import com.hermes.broker.trading.application.port.in.GetOpenOrdersUseCase;
import com.hermes.broker.trading.application.port.out.CancelOrderPort;
import com.hermes.broker.trading.application.port.out.LoadOpenOrdersPort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderManagementService implements GetOpenOrdersUseCase, CancelOrderUseCase {

    private final LoadOpenOrdersPort loadOpenOrdersPort;
    private final CancelOrderPort cancelOrderPort;

    @Override
    public List<OpenOrder> getOpenOrders() {
        return loadOpenOrdersPort.loadOpenOrders();
    }

    @Override
    public void cancelOrder(String orderId, String stockCode, MarketType marketType) {
        cancelOrderPort.cancelOrder(orderId, stockCode, marketType);
    }
}
