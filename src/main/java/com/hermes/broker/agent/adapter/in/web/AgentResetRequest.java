package com.hermes.broker.agent.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record AgentResetRequest(
        @NotBlank String actor,
        @NotBlank String reason,
        @NotBlank String correlationId,
        @NotBlank String confirmation
) {
}
