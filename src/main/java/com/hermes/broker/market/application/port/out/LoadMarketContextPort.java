package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.trading.domain.MarketType;

import java.util.List;
import java.util.Optional;

public interface LoadMarketContextPort {
    Optional<MarketContext> loadById(String contextId);
    Optional<MarketContext> loadLatest(MarketType marketType);
    List<MarketContext> loadHistory(MarketType marketType, int limit);
}
