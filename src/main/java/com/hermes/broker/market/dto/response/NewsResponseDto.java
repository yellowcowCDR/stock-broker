package com.hermes.broker.market.dto.response;

import com.hermes.broker.market.domain.StockNewsResult;

public record NewsResponseDto(
        StockNewsResult result
) {
}
