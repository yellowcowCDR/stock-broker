package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.decision.TradingDecision;

public interface CreateTradingDecisionUseCase {
    TradingDecision createActiveDecision(CreateTradingDecisionCommand command);

    TradingDecision createShadowDecision(CreateTradingDecisionCommand command);
}
