package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.MarketWatchlistResult;

public interface LoadMarketWatchlistPort {
    MarketWatchlistResult loadCandidates();
}
