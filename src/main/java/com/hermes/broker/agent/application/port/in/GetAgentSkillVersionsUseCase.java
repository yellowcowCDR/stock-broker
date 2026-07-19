package com.hermes.broker.agent.application.port.in;

import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;

import java.util.List;
import java.util.Set;

public interface GetAgentSkillVersionsUseCase {
    List<AgentSkill> getVersions(Set<AgentSkillStatus> statuses);
}
