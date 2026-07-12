package com.hermes.broker.market.domain;

public record WatchlistStock(
        String stockCode,
        String stockName,
        String market
) {
}
