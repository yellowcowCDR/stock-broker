package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;

public interface EvaluateOrderRiskUseCase {
    RiskEvaluationResult evaluate(OrderRiskCommand command);
}
