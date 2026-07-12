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

    private final java.util.List<MarketTradingPort> marketTradingPorts;
    private final MarketTimeValidator timeValidator;

    @Override
    public CurrentPriceDto getPrice(String stockCode, MarketType marketType) {
        MarketTradingPort adapter = marketTradingPorts.stream()
                .filter(port -> port.supports(marketType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported market type: " + marketType));

        return adapter.getCurrentPrice(stockCode);
    }
}
