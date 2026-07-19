package com.hermes.broker.agent.application.port.out;

import com.hermes.broker.agent.domain.AgentSkill;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import com.hermes.broker.agent.domain.AgentSkillStatus;

public interface LoadAgentSkillPort {
    Optional<AgentSkill> loadActiveSkill();

    Optional<AgentSkill> loadLatestSkill();

    Optional<AgentSkill> loadByVersion(int version);

    List<AgentSkill> loadVersions(Set<AgentSkillStatus> statuses);
}
