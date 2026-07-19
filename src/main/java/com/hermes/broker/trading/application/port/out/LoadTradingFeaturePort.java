package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadTradingFeaturePort {
    Optional<TradingFeatureSnapshot> loadById(String featureId);

    Optional<TradingFeatureSnapshot> loadByIdempotencyKey(String idempotencyKey);

    Optional<TradingFeatureSnapshot> loadLatest(String stockCode, MarketType marketType);

    List<TradingFeatureSnapshot> loadBetween(Instant startInclusive, Instant endExclusive);
}
