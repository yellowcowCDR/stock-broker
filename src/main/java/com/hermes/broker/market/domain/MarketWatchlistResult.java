package com.hermes.broker.market.domain;

import java.time.Instant;
import java.util.List;

public record MarketWatchlistResult(
        List<WatchlistStock> stocks,
        String dataSource,
        Instant fetchedAt,
        boolean complete,
        String freshness,
        boolean candidateOnly
) {
    public MarketWatchlistResult {
        stocks = stocks == null ? List.of() : List.copyOf(stocks);
    }
}
