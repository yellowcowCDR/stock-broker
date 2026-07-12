package com.hermes.broker.agent.application.port.out;

import com.hermes.broker.agent.domain.AgentSkill;
import java.util.Optional;

public interface LoadAgentSkillPort {
    Optional<AgentSkill> loadActiveSkill();
}
