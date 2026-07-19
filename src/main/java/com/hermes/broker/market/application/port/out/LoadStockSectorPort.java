package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.trading.domain.MarketType;

public interface LoadStockSectorPort {
    boolean supports(MarketType marketType);

    StockSector loadSector(String stockCode);
}
