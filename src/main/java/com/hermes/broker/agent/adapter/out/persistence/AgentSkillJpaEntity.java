package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.domain.AgentSkillStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "agent_skills", uniqueConstraints = {
        @UniqueConstraint(name = "ux_agent_skills_version", columnNames = {"version"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSkillJpaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 20)
    private AgentSkillStatus lifecycleStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "skill_parameters",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private Map<String, Object> skillParameters;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "parent_version")
    private Integer parentVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shadow_evaluation", columnDefinition = "jsonb")
    private Map<String, Object> shadowEvaluation;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @Column(name = "status_changed_by", length = 100)
    private String statusChangedBy;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    @Builder
    public AgentSkillJpaEntity(
            Instant createdAt,
            String description,
            boolean isActive,
            AgentSkillStatus lifecycleStatus,
            Map<String, Object> skillParameters,
            Integer version,
            Integer parentVersion,
            Map<String, Object> shadowEvaluation,
            String statusReason,
            String statusChangedBy,
            Instant statusChangedAt
    ) {
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.description = description;
        this.isActive = isActive;
        this.lifecycleStatus = lifecycleStatus;
        this.skillParameters = skillParameters;
        this.version = version;
        this.parentVersion = parentVersion;
        this.shadowEvaluation = shadowEvaluation;
        this.statusReason = statusReason;
        this.statusChangedBy = statusChangedBy;
        this.statusChangedAt = statusChangedAt;
    }

    public void applyLifecycle(
            AgentSkillStatus status,
            Map<String, Object> evaluation,
            String reason,
            String changedBy,
            Instant changedAt
    ) {
        this.lifecycleStatus = status;
        this.isActive = status == AgentSkillStatus.ACTIVE;
        this.shadowEvaluation = evaluation;
        this.statusReason = reason;
        this.statusChangedBy = changedBy;
        this.statusChangedAt = changedAt;
    }

    public void deactivate() {
        applyLifecycle(
                AgentSkillStatus.ROLLED_BACK,
                shadowEvaluation,
                "Deactivated by administrative reset",
                "BROKER_RESET",
                Instant.now()
        );
    }

    public void resetAsInitial(String actor, String reason) {
        this.description = "Initial default skill after reset";
        this.skillParameters = new java.util.HashMap<>();
        this.parentVersion = null;
        applyLifecycle(
                AgentSkillStatus.ACTIVE,
                null,
                "Administrative PAPER reset: " + reason,
                actor,
                Instant.now()
        );
    }
}
