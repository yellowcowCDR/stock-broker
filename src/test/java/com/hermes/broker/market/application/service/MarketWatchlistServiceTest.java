package com.hermes.broker.market.application.service;

import com.hermes.broker.market.domain.WatchlistStock;
import com.hermes.broker.market.domain.WatchlistCategory;
import com.hermes.broker.market.domain.WatchlistStock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketWatchlistServiceTest {

    private final MarketWatchlistService service = new MarketWatchlistService();

    @Test
    void getWatchlist_returnsHardcodedStocksWithCategory() {
        List<WatchlistStock> watchlist = service.getWatchlist();

        assertThat(watchlist).hasSize(6);
        assertThat(watchlist).extracting(WatchlistStock::stockCode)
                .contains("005930", "000660", "AAPL", "TSLA", "MSFT", "NVDA");
                
        // Check if category is CORE
        assertThat(watchlist).extracting(WatchlistStock::category)
                .containsOnly(WatchlistCategory.CORE);
                
        // Check if score and reasons exist
        assertThat(watchlist.get(0).score()).isNotNull();
        assertThat(watchlist.get(0).reasons()).isNotEmpty();
    }
}
