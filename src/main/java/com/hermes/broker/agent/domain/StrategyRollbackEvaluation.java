package com.hermes.broker.agent.domain;

public record StrategyRollbackEvaluation(
        String currentVersion,
        String previousVersion,
        boolean requiresRollback,
        String reason
) {
}
