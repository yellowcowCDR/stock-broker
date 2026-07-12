package com.hermes.broker.agent.domain;

import java.time.LocalDateTime;
import java.util.Map;

public record AgentSkill(
        Long id,
        LocalDateTime createdAt,
        String description,
        boolean active,
        Map<String, Object> skillParameters,
        int version
) {
    public AgentSkill {
        skillParameters = skillParameters == null
                ? Map.of()
                : Map.copyOf(skillParameters);
    }

    public AgentSkill deactivate() {
        return new AgentSkill(
                id,
                createdAt,
                description,
                false,
                skillParameters,
                version
        );
    }

    public static AgentSkill createNextVersion(
            AgentSkill current,
            String description,
            Map<String, Object> newParameters
    ) {
        return new AgentSkill(
                null,
                LocalDateTime.now(),
                description,
                true,
                newParameters,
                current.version() + 1
        );
    }

    public static AgentSkill createInitial(
            String description,
            Map<String, Object> skillParameters
    ) {
        return new AgentSkill(
                null,
                LocalDateTime.now(),
                description,
                true,
                skillParameters,
                1
        );
    }
}
