package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.decision.TradingDecision;
import java.util.List;

public interface GetTradingDecisionUseCase {
    List<TradingDecision> getDecisionsByDate(String date);
}
