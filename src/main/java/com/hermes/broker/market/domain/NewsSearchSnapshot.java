package com.hermes.broker.market.domain;

import java.time.Instant;
import java.util.List;

public record NewsSearchSnapshot(
        String query,
        List<StockNewsArticle> articles,
        long totalAvailable,
        Instant fetchedAt,
        String dataSource,
        boolean complete
) {
    public NewsSearchSnapshot {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
