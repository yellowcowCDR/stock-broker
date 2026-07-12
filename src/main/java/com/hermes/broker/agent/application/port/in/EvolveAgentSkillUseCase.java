package com.hermes.broker.agent.application.port.in;

import com.hermes.broker.agent.domain.AgentSkill;

public interface EvolveAgentSkillUseCase {
    AgentSkill evolve(EvolveAgentSkillCommand command);
}
