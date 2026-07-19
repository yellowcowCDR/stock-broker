package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.out.LoadOpenOrdersPort;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.dto.OrderRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.hermes.broker.trading.domain.OverseasExchange;

@Component
@RequiredArgsConstructor
public class DuplicateOrderValidator {

    private final TradingLogRepository tradingLogRepository;
    private final java.util.List<LoadOpenOrdersPort> loadOpenOrdersPorts;

    public void validate(String accountKey, OrderRequestDto request) {
        if (tradingLogRepository.existsOpenOrder(
                accountKey, request.getMarketType(), request.getStockCode(), request.getOrderType())) {
            throw new IllegalStateException("Broker already has an open order for the same stock and direction.");
        }

        LoadOpenOrdersPort loadOpenOrdersPort = loadOpenOrdersPorts.stream()
                .filter(port -> port.supports(request.getMarketType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No KIS open-order provider supports " + request.getMarketType() + "."));
        boolean existsAtBroker = loadOpenOrdersPort.loadOpenOrders().stream()
                .anyMatch(order -> order.marketType() == request.getMarketType()
                        && order.stockCode().equalsIgnoreCase(request.getStockCode())
                        && (request.getMarketType() != com.hermes.broker.trading.domain.MarketType.OVERSEAS
                        || order.exchangeCode().equalsIgnoreCase(
                                OverseasExchange.from(request.getExchangeCode()).orderExchangeCode()))
                        && order.orderType() == request.getOrderType());
        if (existsAtBroker) {
            throw new IllegalStateException("KIS already has an open order for the same stock and direction.");
        }
    }
}
