package com.hermes.broker.agent.application.port.in;

import java.util.Map;

public record EvolveAgentSkillCommand(
        String description,
        Map<String, Object> skillParameters
) {
    public EvolveAgentSkillCommand {
        skillParameters = skillParameters == null
                ? Map.of()
                : Map.copyOf(skillParameters);
    }
}
