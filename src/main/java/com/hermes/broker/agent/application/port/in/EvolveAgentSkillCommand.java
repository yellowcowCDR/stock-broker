package com.hermes.broker.agent.application.port.in;

import java.util.Map;

public record EvolveAgentSkillCommand(
        String description,
        Map<String, Object> skillParameters,
        String createdBy
) {
    public EvolveAgentSkillCommand {
        skillParameters = skillParameters == null
                ? Map.of()
                : Map.copyOf(skillParameters);
        createdBy = createdBy == null || createdBy.isBlank()
                ? "HERMES_LEGACY_PUT"
                : createdBy;
    }

    public EvolveAgentSkillCommand(String description, Map<String, Object> skillParameters) {
        this(description, skillParameters, "HERMES_LEGACY_PUT");
    }
}
