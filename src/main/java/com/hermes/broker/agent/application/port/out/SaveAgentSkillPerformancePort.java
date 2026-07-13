package com.hermes.broker.agent.application.port.out;

import com.hermes.broker.agent.domain.AgentSkillPerformance;

public interface SaveAgentSkillPerformancePort {
    void save(AgentSkillPerformance performance);
}
