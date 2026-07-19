package com.hermes.broker.market.domain;

import com.hermes.broker.trading.domain.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketContext(
        String contextId,
        MarketType marketType,
        MarketEntryPolicy entryPolicy,
        BigDecimal riskMultiplier,
        MarketOverview overviewSnapshot,
        List<String> rationale,
        String analyzedBy,
        String correlationId,
        Instant analyzedAt,
        Instant validUntil
) {
}
