package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import java.util.Optional;

public interface GetTradingFeatureUseCase {
    Optional<TradingFeatureSnapshot> getLatestFeature(String stockCode, MarketType marketType);
    java.util.List<TradingFeatureSnapshot> getFeaturesByDate(String date);
}
