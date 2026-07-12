package com.hermes.broker.market.domain;

import java.time.LocalDateTime;

public record StockNews(
        String title,
        String summary,
        NewsSentiment sentiment,
        LocalDateTime publishedAt
) {
}
