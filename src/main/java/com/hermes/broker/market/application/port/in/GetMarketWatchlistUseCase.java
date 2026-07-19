package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.domain.MarketWatchlistResult;

public interface GetMarketWatchlistUseCase {
    MarketWatchlistResult getWatchlist();
}
