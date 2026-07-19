package com.hermes.broker.agent.application.port.in;

import com.hermes.broker.agent.domain.AgentSkill;

public interface RollbackAgentSkillUseCase {
    AgentSkill rollback(int targetVersion, String approvedBy, String reason);
}
