package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.domain.AgentSkill;
import org.springframework.stereotype.Component;

@Component
public class AgentSkillPersistenceMapper {

    public AgentSkill toDomain(AgentSkillJpaEntity entity) {
        return new AgentSkill(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getDescription(),
                entity.isActive(),
                entity.getSkillParameters(),
                entity.getVersion()
        );
    }

    public AgentSkillJpaEntity toEntity(AgentSkill domain) {
        return AgentSkillJpaEntity.builder()
                .createdAt(domain.createdAt())
                .description(domain.description())
                .isActive(domain.active())
                .skillParameters(domain.skillParameters())
                .version(domain.version())
                .build();
    }
}
