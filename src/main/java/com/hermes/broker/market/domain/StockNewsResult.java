package com.hermes.broker.market.domain;

import java.time.Instant;
import java.util.List;

public record StockNewsResult(
        String stockCode,
        String query,
        List<StockNewsArticle> articles,
        long totalAvailable,
        int totalAnalyzed,
        String dataSource,
        Instant fetchedAt,
        boolean complete,
        String freshness,
        String analysisMethod
) {
    public StockNewsResult {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
