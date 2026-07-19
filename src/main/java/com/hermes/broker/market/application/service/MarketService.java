package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.GetMarketPriceUseCase;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService implements GetMarketPriceUseCase {

    private final List<MarketTradingPort> marketTradingPorts;

    @Override
    public CurrentPriceDto getPrice(String stockCode, MarketType marketType, String exchangeCode) {
        MarketTradingPort adapter = marketTradingPorts.stream()
                .filter(port -> port.supports(marketType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported market type: " + marketType));

        if (marketType == MarketType.OVERSEAS) {
            return adapter.getCurrentPrice(stockCode,
                    com.hermes.broker.trading.domain.OverseasExchange.from(exchangeCode)
                            .orderExchangeCode());
        }
        return adapter.getCurrentPrice(stockCode);
    }
}
