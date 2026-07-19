package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.application.port.out.LoadStockSectorPort;
import com.hermes.broker.market.application.port.out.LoadUsFundamentalsPort;
import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.market.domain.UsFundamentalsSnapshot;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AlphaVantageStockSectorAdapter implements LoadStockSectorPort {

    private final LoadUsFundamentalsPort fundamentalsPort;

    @Override
    public boolean supports(MarketType marketType) {
        return marketType == MarketType.OVERSEAS;
    }

    @Override
    public StockSector loadSector(String stockCode) {
        String symbol = stockCode.trim().toUpperCase(Locale.ROOT);
        UsFundamentalsSnapshot snapshot = fundamentalsPort.load(symbol);
        if (snapshot == null || snapshot.companyOverview() == null || snapshot.fetchedAt() == null) {
            throw new MarketDataUnavailableException(
                    "Alpha Vantage company overview is unavailable for " + symbol + ".");
        }
        String sector = snapshot.companyOverview().entrySet().stream()
                .filter(entry -> "sector".equalsIgnoreCase(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank() && !"None".equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "Alpha Vantage company overview has no sector for " + symbol + "."));

        return new StockSector(
                symbol,
                MarketType.OVERSEAS,
                sector.trim().toUpperCase(Locale.ROOT).replace(' ', '_'),
                sector.trim(),
                "GICS_SECTOR_PROVIDER_VALUE",
                "ALPHA_VANTAGE:OVERVIEW:Sector",
                snapshot.fetchedAt(),
                true
        );
    }
}
