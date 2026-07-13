package com.hermes.broker.agent.application.port.in;

import com.hermes.broker.agent.domain.AgentSkillPerformance;

public interface EvaluateStrategyPerformanceUseCase {
    AgentSkillPerformance evaluate(String strategyVersion);
}
