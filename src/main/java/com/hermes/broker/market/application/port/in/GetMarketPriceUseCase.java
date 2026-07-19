package com.hermes.broker.market.application.port.in;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.market.dto.CurrentPriceDto;

public interface GetMarketPriceUseCase {
    default CurrentPriceDto getPrice(String stockCode, MarketType marketType) {
        return getPrice(stockCode, marketType, null);
    }

    CurrentPriceDto getPrice(String stockCode, MarketType marketType, String exchangeCode);
}
