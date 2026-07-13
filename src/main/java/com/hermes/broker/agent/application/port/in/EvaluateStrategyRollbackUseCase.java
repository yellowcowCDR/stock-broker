package com.hermes.broker.agent.application.port.in;

import com.hermes.broker.agent.domain.StrategyRollbackEvaluation;

public interface EvaluateStrategyRollbackUseCase {
    StrategyRollbackEvaluation evaluateRollback(String currentVersion);
}
