package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import com.hermes.broker.market.dto.PortfolioDto;
import com.hermes.broker.trading.domain.MarketType;

public interface MarketTradingPort {
    boolean supports(MarketType marketType);
    CurrentPriceDto getCurrentPrice(String stockCode);
    OrderResponseDto placeOrder(OrderRequestDto orderRequest);
    PortfolioDto getPortfolio();
}
