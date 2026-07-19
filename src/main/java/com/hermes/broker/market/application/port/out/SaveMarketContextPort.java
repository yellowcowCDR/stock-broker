package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.MarketContext;

public interface SaveMarketContextPort {
    MarketContext save(MarketContext context);
}
