package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.decision.TradingDecision;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;

public interface LoadTradingDecisionPort {
    Optional<TradingDecision> loadById(String decisionId);

    Optional<TradingDecision> loadByIdempotencyKey(String idempotencyKey);

    boolean existsByFeatureAndStrategyAndMode(
            String featureId, String strategyVersion, TradingDecisionMode mode);

    List<TradingDecision> loadBetween(Instant startInclusive, Instant endExclusive);
}
