package com.hermes.broker.agent.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
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

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime createdAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "skill_parameters",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private Map<String, Object> skillParameters;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Builder
    public AgentSkillJpaEntity(
            LocalDateTime createdAt,
            String description,
            boolean isActive,
            Map<String, Object> skillParameters,
            Integer version
    ) {
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.description = description;
        this.isActive = isActive;
        this.skillParameters = skillParameters;
        this.version = version;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
