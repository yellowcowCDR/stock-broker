package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.dto.response.NewsResponseDto;

public interface MarketNewsUseCase {
    NewsResponseDto getNews(String stockCode);
}
