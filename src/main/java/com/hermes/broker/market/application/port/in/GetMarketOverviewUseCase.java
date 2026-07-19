package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;

public interface GetMarketOverviewUseCase {
    MarketOverview getOverview(MarketType marketType);
}
