package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.trading.domain.MarketType;

import java.util.List;
import java.util.Optional;

public interface MarketContextUseCase {
    MarketContext create(CreateMarketContextCommand command);
    Optional<MarketContext> getLatest(MarketType marketType);
    List<MarketContext> getHistory(MarketType marketType);
}
