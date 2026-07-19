package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.domain.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MarketServiceTest {

    @Test
    void returnsKisPriceAsIsWithoutFabricatingTechnicalIndicators() {
        MarketTradingPort port = mock(MarketTradingPort.class);
        CurrentPriceDto kisPrice = CurrentPriceDto.builder()
                .stockCode("005930")
                .currentPrice(new BigDecimal("81200"))
                .changeRate(new BigDecimal("1.25"))
                .accumulatedVolume(1234567L)
                .build();
        given(port.supports(MarketType.DOMESTIC)).willReturn(true);
        given(port.getCurrentPrice("005930")).willReturn(kisPrice);

        CurrentPriceDto result = new MarketService(List.of(port)).getPrice("005930", MarketType.DOMESTIC);

        assertThat(result).isSameAs(kisPrice);
        assertThat(result.getTechnicalIndicators()).isNull();
    }
}
