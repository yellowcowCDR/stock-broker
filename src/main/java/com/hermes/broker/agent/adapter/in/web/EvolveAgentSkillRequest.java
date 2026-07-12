package com.hermes.broker.agent.adapter.in.web;

import java.util.Map;

public record EvolveAgentSkillRequest(
        String description,
        Map<String, Object> skillParameters
) {
}
