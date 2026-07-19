package com.hermes.broker.market.domain;

import com.hermes.broker.trading.domain.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketOverview(
        MarketType marketType,
        List<MarketSegmentOverview> segments,
        long advancingIssues,
        long decliningIssues,
        long unchangedIssues,
        BigDecimal breadthScore,
        BigDecimal foreignNetBuyTradingValue,
        BigDecimal individualNetBuyTradingValue,
        BigDecimal institutionNetBuyTradingValue,
        String tradingValueUnit,
        String dataSource,
        Instant fetchedAt,
        Instant validUntil,
        boolean complete,
        String freshness
) {
}
