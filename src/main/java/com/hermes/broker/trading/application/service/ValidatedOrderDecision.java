package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;

public record ValidatedOrderDecision(
        TradingDecision decision,
        TradingFeatureSnapshot feature
) {
}
