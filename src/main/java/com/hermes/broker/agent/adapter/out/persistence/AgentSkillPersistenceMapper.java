package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.domain.AgentSkill;
import org.springframework.stereotype.Component;

@Component
public class AgentSkillPersistenceMapper {

    public AgentSkill toDomain(AgentSkillJpaEntity entity) {
        return new AgentSkill(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getStatusChangedAt() != null
                        ? entity.getStatusChangedAt() : entity.getCreatedAt(),
                entity.getDescription(),
                entity.getLifecycleStatus() != null
                        ? entity.getLifecycleStatus()
                        : (entity.isActive()
                            ? com.hermes.broker.agent.domain.AgentSkillStatus.ACTIVE
                            : com.hermes.broker.agent.domain.AgentSkillStatus.ROLLED_BACK),
                entity.getSkillParameters(),
                entity.getVersion(),
                entity.getParentVersion(),
                entity.getShadowEvaluation(),
                entity.getStatusReason(),
                entity.getStatusChangedBy()
        );
    }

    public AgentSkillJpaEntity toEntity(AgentSkill domain) {
        return AgentSkillJpaEntity.builder()
                .createdAt(domain.createdAt())
                .description(domain.description())
                .isActive(domain.active())
                .lifecycleStatus(domain.status())
                .skillParameters(domain.skillParameters())
                .version(domain.version())
                .parentVersion(domain.parentVersion())
                .shadowEvaluation(domain.shadowEvaluation())
                .statusReason(domain.statusReason())
                .statusChangedBy(domain.statusChangedBy())
                .statusChangedAt(domain.statusChangedAt())
                .build();
    }
}
