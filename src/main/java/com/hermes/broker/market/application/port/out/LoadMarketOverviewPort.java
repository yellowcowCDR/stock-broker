package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;

public interface LoadMarketOverviewPort {
    boolean supports(MarketType marketType);
    MarketOverview loadOverview();
}
