package com.hermes.broker.agent.domain;

import java.time.Instant;
import java.util.Map;

public record AgentSkill(
        Long id,
        Instant createdAt,
        Instant statusChangedAt,
        String description,
        AgentSkillStatus status,
        Map<String, Object> skillParameters,
        int version,
        Integer parentVersion,
        Map<String, Object> shadowEvaluation,
        String statusReason,
        String statusChangedBy
) {
    public AgentSkill {
        status = status == null ? AgentSkillStatus.CANDIDATE : status;
        skillParameters = skillParameters == null
                ? Map.of()
                : Map.copyOf(skillParameters);
        shadowEvaluation = shadowEvaluation == null
                ? Map.of()
                : Map.copyOf(shadowEvaluation);
    }

    public boolean active() {
        return status == AgentSkillStatus.ACTIVE;
    }

    public AgentSkill startShadow(String changedBy, String reason, Instant changedAt) {
        requireStatus(AgentSkillStatus.CANDIDATE, "start shadow evaluation");
        return new AgentSkill(
                id,
                createdAt,
                changedAt,
                description,
                AgentSkillStatus.SHADOW,
                skillParameters,
                version,
                parentVersion,
                Map.of(),
                reason,
                changedBy
        );
    }

    public AgentSkill recordShadowEvaluation(
            Map<String, Object> evaluation,
            String changedBy,
            String reason,
            Instant changedAt
    ) {
        requireStatus(AgentSkillStatus.SHADOW, "record shadow evaluation");
        if (evaluation == null || evaluation.isEmpty()) {
            throw new IllegalArgumentException("shadow evaluation cannot be empty");
        }
        return new AgentSkill(
                id,
                createdAt,
                changedAt,
                description,
                status,
                skillParameters,
                version,
                parentVersion,
                evaluation,
                reason,
                changedBy
        );
    }

    public AgentSkill transitionTo(
            AgentSkillStatus nextStatus,
            String changedBy,
            String reason,
            Instant changedAt
    ) {
        return new AgentSkill(
                id,
                createdAt,
                changedAt,
                description,
                nextStatus,
                skillParameters,
                version,
                parentVersion,
                shadowEvaluation,
                reason,
                changedBy
        );
    }

    public static AgentSkill createCandidate(
            AgentSkill latest,
            AgentSkill active,
            String description,
            Map<String, Object> newParameters,
            String createdBy,
            Instant createdAt
    ) {
        return new AgentSkill(
                null,
                createdAt,
                createdAt,
                description,
                AgentSkillStatus.CANDIDATE,
                newParameters,
                latest.version() + 1,
                active.version(),
                Map.of(),
                "Candidate created; activation requires shadow evaluation and explicit approval",
                createdBy
        );
    }

    public static AgentSkill createInitial(
            String description,
            Map<String, Object> skillParameters,
            Instant createdAt
    ) {
        return new AgentSkill(
                null,
                createdAt,
                createdAt,
                description,
                AgentSkillStatus.ACTIVE,
                skillParameters,
                1,
                null,
                Map.of(),
                "Initial strategy",
                "BROKER_BOOTSTRAP"
        );
    }

    public static AgentSkill createInitial(
            String description,
            Map<String, Object> skillParameters
    ) {
        return createInitial(description, skillParameters, Instant.now());
    }

    private void requireStatus(AgentSkillStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + operation + " for strategy version " + version
                            + " in status " + status + "; expected " + expected + "."
            );
        }
    }
}
