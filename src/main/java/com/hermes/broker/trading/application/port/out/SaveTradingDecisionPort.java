package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.decision.TradingDecision;

public interface SaveTradingDecisionPort {
    void save(TradingDecision decision);
}
