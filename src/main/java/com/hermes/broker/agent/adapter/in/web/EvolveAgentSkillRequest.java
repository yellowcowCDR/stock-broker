package com.hermes.broker.agent.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record EvolveAgentSkillRequest(
        @NotBlank String description,
        @NotEmpty Map<String, Object> skillParameters,
        String createdBy,
        Boolean activate
) {
}
