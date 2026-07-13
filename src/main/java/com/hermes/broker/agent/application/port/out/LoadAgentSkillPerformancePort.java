package com.hermes.broker.agent.application.port.out;

import com.hermes.broker.agent.domain.AgentSkillPerformance;
import java.util.Optional;

public interface LoadAgentSkillPerformancePort {
    Optional<AgentSkillPerformance> loadPerformance(String skillVersion);
}
