package com.hermes.broker.market.dto.response;

import com.hermes.broker.market.domain.StockIntelligence;

public record IntelligenceResponseDto(
        StockIntelligence intelligence
) {
}
