package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.GetMarketPriceUseCase;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService implements GetMarketPriceUseCase {

    private final List<MarketTradingPort> marketTradingPorts;
    private final MarketTimeValidator timeValidator;

    @Override
    public CurrentPriceDto getPrice(String stockCode, MarketType marketType) {
        MarketTradingPort adapter = marketTradingPorts.stream()
                .filter(port -> port.supports(marketType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported market type: " + marketType));

        CurrentPriceDto currentPrice = adapter.getCurrentPrice(stockCode);
        TechnicalIndicators indicators = createMockIndicators(currentPrice.getCurrentPrice());

        return CurrentPriceDto.builder()
                .stockCode(currentPrice.getStockCode())
                .currentPrice(currentPrice.getCurrentPrice())
                .changeRate(currentPrice.getChangeRate())
                .accumulatedVolume(currentPrice.getAccumulatedVolume())
                .technicalIndicators(indicators)
                .build();
    }

    private TechnicalIndicators createMockIndicators(BigDecimal currentPrice) {
        BigDecimal ma5 = currentPrice
                .multiply(new BigDecimal("0.99"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal ma20 = currentPrice
                .multiply(new BigDecimal("0.97"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal ma60 = currentPrice
                .multiply(new BigDecimal("0.94"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal rsi14 = new BigDecimal("55.00");
        return new TechnicalIndicators(ma5, ma20, ma60, rsi14);
    }
}
