package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.application.port.in.GetMarketWatchlistUseCase;
import com.hermes.broker.market.application.port.out.LoadMarketWatchlistPort;
import com.hermes.broker.market.domain.MarketWatchlistResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketWatchlistService implements GetMarketWatchlistUseCase {

    private final LoadMarketWatchlistPort loadMarketWatchlistPort;

    @Override
    public MarketWatchlistResult getWatchlist() {
        MarketWatchlistResult result = loadMarketWatchlistPort.loadCandidates();
        if (result == null || !result.complete() || result.stocks().isEmpty()) {
            throw new MarketDataUnavailableException("A complete real-market watchlist is unavailable.");
        }
        return result;
    }
}
