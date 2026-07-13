package com.hermes.broker.market.domain;

import java.time.LocalDateTime;

public record StockNewsArticle(
        String title,
        String description,
        String url,
        LocalDateTime publishedAt,
        double qualityScore
) {
}
