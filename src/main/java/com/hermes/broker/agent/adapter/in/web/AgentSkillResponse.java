package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import java.time.Instant;
import java.util.Map;

public record AgentSkillResponse(
        Long id,
        Instant createdAt,
        Instant statusChangedAt,
        String description,
        boolean active,
        AgentSkillStatus status,
        Map<String, Object> skillParameters,
        int version,
        Integer parentVersion,
        Map<String, Object> shadowEvaluation,
        String statusReason,
        String statusChangedBy
) {
    public static AgentSkillResponse from(AgentSkill domain) {
        return new AgentSkillResponse(
                domain.id(),
                domain.createdAt(),
                domain.statusChangedAt(),
                domain.description(),
                domain.active(),
                domain.status(),
                domain.skillParameters(),
                domain.version(),
                domain.parentVersion(),
                domain.shadowEvaluation(),
                domain.statusReason(),
                domain.statusChangedBy()
        );
    }
}
