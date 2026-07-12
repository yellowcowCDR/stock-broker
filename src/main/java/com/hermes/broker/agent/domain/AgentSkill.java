package com.hermes.broker.agent.domain;

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
@Table(name = "agent_skills")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer version;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> skillParameters;

    @Builder
    public AgentSkill(Integer version, String description, boolean isActive, Map<String, Object> skillParameters) {
        this.version = version;
        this.description = description;
        this.isActive = isActive;
        this.skillParameters = skillParameters;
        this.createdAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
    }
}
