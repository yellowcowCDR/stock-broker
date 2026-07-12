package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.GetMarketWatchlistUseCase;
import com.hermes.broker.market.domain.WatchlistStock;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketWatchlistService implements GetMarketWatchlistUseCase {

    @Override
    public List<WatchlistStock> getWatchlist() {
        return List.of(
                new WatchlistStock("005930", "삼성전자", "KRX"),
                new WatchlistStock("000660", "SK하이닉스", "KRX"),
                new WatchlistStock("AAPL", "Apple", "NASDAQ"),
                new WatchlistStock("TSLA", "Tesla", "NASDAQ"),
                new WatchlistStock("MSFT", "Microsoft", "NASDAQ"),
                new WatchlistStock("NVDA", "NVIDIA", "NASDAQ")
        );
    }
}
