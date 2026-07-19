package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.TradingDecision;

public record ShadowDecisionResult(
        TradingDecision decision,
        ShadowPerformanceSample sample,
        boolean replayed
) {
}
