package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.GetMarketPriceUseCase;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService implements GetMarketPriceUseCase {

    private final MarketTradingPort marketTradingPort;
    private final MarketTimeValidator timeValidator;

    @Override
    public CurrentPriceDto getPrice(String stockCode, MarketType marketType) {
        timeValidator.validateMarketOpen();

        if (marketType == MarketType.OVERSEAS) {
            throw new UnsupportedOperationException("Overseas market is not fully implemented yet.");
        }

        return marketTradingPort.getCurrentPrice(stockCode);
    }
}
