package com.hermes.broker.agent.application.port.out;

import com.hermes.broker.agent.domain.AgentSkill;

public interface SaveAgentSkillPort {
    AgentSkill save(AgentSkill agentSkill);
}
