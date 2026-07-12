package com.hermes.broker.market.application.service;

import com.hermes.broker.market.domain.WatchlistStock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketWatchlistServiceTest {

    private final MarketWatchlistService service = new MarketWatchlistService();

    @Test
    void getWatchlist_returnsHardcodedStocks() {
        List<WatchlistStock> watchlist = service.getWatchlist();

        assertThat(watchlist).hasSize(6);
        assertThat(watchlist).extracting(WatchlistStock::stockCode)
                .contains("005930", "000660", "AAPL", "TSLA", "MSFT", "NVDA");
    }
}
