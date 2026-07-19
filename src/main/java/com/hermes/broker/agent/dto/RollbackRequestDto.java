package com.hermes.broker.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record RollbackRequestDto(
        @Positive int targetVersion,
        @NotBlank String reason,
        @NotBlank String approvedBy
) {}
