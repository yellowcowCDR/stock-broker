package com.hermes.broker.agent.application.port.in;

import com.hermes.broker.agent.domain.AgentSkill;

public interface ManageAgentSkillLifecycleUseCase {
    AgentSkill getByVersion(int version);

    AgentSkill startShadow(int version, String changedBy, String reason);

    AgentSkill evaluateShadow(int version, String evaluatedBy, String reason);

    AgentSkill promote(int version, String approvedBy, String reason);

    AgentSkill reject(int version, String rejectedBy, String reason);
}
