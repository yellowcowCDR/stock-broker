package com.hermes.broker.trading.application.service;

import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MarketContextGuard {

    private final LoadMarketContextPort loadMarketContextPort;
    private final Clock clock;

    public MarketContext validateEntry(MarketType marketType) {
        return validateEntry(marketType, null);
    }

    public MarketContext validateEntry(MarketType marketType, String expectedContextId) {
        MarketContext context = loadMarketContextPort.loadLatest(marketType)
                .orElseThrow(() -> new IllegalStateException(
                        "No market context exists for " + marketType + ". New BUY orders are blocked."));
        Instant now = clock.instant();
        if (context.marketType() != marketType
                || context.overviewSnapshot() == null
                || context.overviewSnapshot().marketType() != marketType) {
            throw new IllegalStateException("Market context does not match the order market.");
        }
        if (expectedContextId != null && !expectedContextId.isBlank()
                && !expectedContextId.equals(context.contextId())) {
            throw new IllegalStateException("Decision market context " + expectedContextId
                    + " is no longer the latest context " + context.contextId()
                    + ". New BUY orders require a fresh Feature and Decision.");
        }
        if (context.validUntil() == null || !now.isBefore(context.validUntil())
                || context.overviewSnapshot().validUntil() == null
                || !now.isBefore(context.overviewSnapshot().validUntil())) {
            throw new IllegalStateException("Market context is stale. New BUY orders are blocked.");
        }
        if (!context.overviewSnapshot().complete()) {
            throw new IllegalStateException("Market context contains an incomplete market overview.");
        }
        if (context.entryPolicy() == MarketEntryPolicy.BLOCK_NEW_ENTRIES) {
            throw new IllegalStateException("Market context blocks new entries.");
        }
        if (context.riskMultiplier() == null
                || context.riskMultiplier().compareTo(BigDecimal.ZERO) <= 0
                || context.riskMultiplier().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("Market context riskMultiplier is not orderable.");
        }
        return context;
    }
}
