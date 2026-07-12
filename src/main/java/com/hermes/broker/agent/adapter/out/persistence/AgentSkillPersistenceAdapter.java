package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgentSkillPersistenceAdapter implements LoadAgentSkillPort, SaveAgentSkillPort {

    private final AgentSkillJpaRepository repository;
    private final AgentSkillPersistenceMapper mapper;

    @Override
    public Optional<AgentSkill> loadActiveSkill() {
        return repository.findFirstByIsActiveTrueOrderByVersionDesc()
                .map(mapper::toDomain);
    }

    @Override
    public AgentSkill save(AgentSkill agentSkill) {
        AgentSkillJpaEntity entity;
        if (agentSkill.id() != null) {
            // Update existing entity (mainly for deactivation)
            entity = repository.findById(agentSkill.id())
                    .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + agentSkill.id()));
            if (!agentSkill.active()) {
                entity.deactivate();
            }
        } else {
            // Insert new entity
            entity = mapper.toEntity(agentSkill);
        }
        
        AgentSkillJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}
