package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.application.port.in.GetMarketOverviewUseCase;
import com.hermes.broker.market.application.port.out.LoadMarketOverviewPort;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketOverviewService implements GetMarketOverviewUseCase {

    private final List<LoadMarketOverviewPort> ports;
    private final Clock clock;

    @Override
    public MarketOverview getOverview(MarketType marketType) {
        LoadMarketOverviewPort port = ports.stream()
                .filter(candidate -> candidate.supports(marketType))
                .findFirst()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No complete real-market overview provider supports " + marketType + "."));
        MarketOverview overview = port.loadOverview();
        if (overview == null || !overview.complete() || overview.segments() == null
                || overview.segments().isEmpty() || overview.validUntil() == null
                || !clock.instant().isBefore(overview.validUntil())) {
            throw new MarketDataUnavailableException(
                    "A complete and fresh real-market overview is unavailable for " + marketType + ".");
        }
        return overview;
    }
}
