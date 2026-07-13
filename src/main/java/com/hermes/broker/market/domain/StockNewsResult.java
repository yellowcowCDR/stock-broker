package com.hermes.broker.market.domain;

import java.util.List;

public record StockNewsResult(
        String stockCode,
        List<StockNewsArticle> articles,
        int totalAnalyzed
) {
}
