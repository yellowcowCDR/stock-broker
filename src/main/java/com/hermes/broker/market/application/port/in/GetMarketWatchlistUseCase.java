package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.domain.WatchlistStock;
import java.util.List;

public interface GetMarketWatchlistUseCase {
    List<WatchlistStock> getWatchlist();
}
