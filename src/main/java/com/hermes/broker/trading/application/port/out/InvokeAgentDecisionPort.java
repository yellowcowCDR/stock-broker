package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;

public interface InvokeAgentDecisionPort {
    TradingDecision invoke(TradingFeatureSnapshot snapshot);
}
