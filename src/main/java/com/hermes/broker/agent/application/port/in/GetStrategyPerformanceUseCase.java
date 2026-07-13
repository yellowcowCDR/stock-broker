package com.hermes.broker.agent.application.port.in;

import com.hermes.broker.agent.domain.AgentSkillPerformance;

public interface GetStrategyPerformanceUseCase {
    AgentSkillPerformance getPerformance(String version);
}
