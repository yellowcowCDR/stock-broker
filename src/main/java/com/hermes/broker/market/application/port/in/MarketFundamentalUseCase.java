package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.dto.response.FundamentalsResponseDto;

public interface MarketFundamentalUseCase {
    FundamentalsResponseDto getFundamentals(String stockCode);
}
