package com.hermes.broker.market.domain;

import java.time.Instant;

public record StockNewsArticle(
        String title,
        String description,
        String url,
        String originalUrl,
        String source,
        Instant publishedAt,
        double qualityScore,
        double relevanceScore,
        double sentimentScore,
        NewsSentiment sentiment
) {
}
