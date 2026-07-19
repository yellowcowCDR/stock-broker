package com.hermes.broker.agent.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record StrategyLifecycleRequest(
        @NotBlank String actor,
        @NotBlank String reason
) {
}
