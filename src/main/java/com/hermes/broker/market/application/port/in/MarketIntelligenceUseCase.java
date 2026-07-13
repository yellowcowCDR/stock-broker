package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.dto.response.IntelligenceResponseDto;

public interface MarketIntelligenceUseCase {
    IntelligenceResponseDto getIntelligence(String stockCode);
}
