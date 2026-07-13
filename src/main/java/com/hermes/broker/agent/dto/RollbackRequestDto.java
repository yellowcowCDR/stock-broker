package com.hermes.broker.agent.dto;

public record RollbackRequestDto(
        String targetVersion,
        String reason
) {}
