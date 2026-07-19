package com.hermes.broker.market.domain;

import com.hermes.broker.trading.domain.MarketType;

import java.time.Instant;

public record StockSector(
        String stockCode,
        MarketType marketType,
        String sectorCode,
        String sectorName,
        String classificationLevel,
        String dataSource,
        Instant fetchedAt,
        boolean complete
) {
}
