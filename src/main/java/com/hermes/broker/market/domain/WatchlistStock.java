package com.hermes.broker.market.domain;

import java.math.BigDecimal;
import java.util.List;

public record WatchlistStock(
        String stockCode,
        String stockName,
        String market,
        WatchlistCategory category,
        BigDecimal score,
        List<String> reasons
) {
}
