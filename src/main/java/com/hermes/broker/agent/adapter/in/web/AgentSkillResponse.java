package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.domain.AgentSkill;
import java.time.LocalDateTime;
import java.util.Map;

public record AgentSkillResponse(
        Long id,
        LocalDateTime createdAt,
        String description,
        boolean active,
        Map<String, Object> skillParameters,
        int version
) {
    public static AgentSkillResponse from(AgentSkill domain) {
        return new AgentSkillResponse(
                domain.id(),
                domain.createdAt(),
                domain.description(),
                domain.active(),
                domain.skillParameters(),
                domain.version()
        );
    }
}
