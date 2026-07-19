package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.trading.domain.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateMarketContextCommand(
        MarketType marketType,
        MarketEntryPolicy entryPolicy,
        BigDecimal riskMultiplier,
        Instant validUntil,
        List<String> rationale,
        String analyzedBy,
        String correlationId
) {
}
