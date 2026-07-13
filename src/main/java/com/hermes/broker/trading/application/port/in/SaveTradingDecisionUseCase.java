package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.decision.TradingDecision;

public interface SaveTradingDecisionUseCase {
    void save(TradingDecision decision);
}
