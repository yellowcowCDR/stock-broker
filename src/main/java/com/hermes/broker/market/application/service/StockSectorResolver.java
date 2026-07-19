package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.application.port.out.LoadStockSectorPort;
import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockSectorResolver {

    private final List<LoadStockSectorPort> ports;

    public StockSector resolve(String stockCode, MarketType marketType) {
        LoadStockSectorPort port = ports.stream()
                .filter(candidate -> candidate.supports(marketType))
                .findFirst()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No real sector-data provider supports " + marketType + "."
                ));
        StockSector sector = port.loadSector(stockCode);
        if (sector == null || !sector.complete() || sector.sectorName() == null
                || sector.sectorName().isBlank()) {
            throw new MarketDataUnavailableException(
                    "Complete sector data is unavailable for " + marketType + ":" + stockCode + "."
            );
        }
        return sector;
    }
}
