package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import com.hermes.broker.agent.domain.AgentSkillStatus;

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
    public Optional<AgentSkill> loadLatestSkill() {
        return repository.findFirstByOrderByVersionDesc().map(mapper::toDomain);
    }

    @Override
    public Optional<AgentSkill> loadByVersion(int version) {
        return repository.findByVersion(version).map(mapper::toDomain);
    }

    @Override
    public List<AgentSkill> loadVersions(Set<AgentSkillStatus> statuses) {
        Set<AgentSkillStatus> filter = statuses == null ? Set.of() : Set.copyOf(statuses);
        return repository.findAllByOrderByVersionDesc().stream()
                .map(mapper::toDomain)
                .filter(skill -> filter.isEmpty() || filter.contains(skill.status()))
                .toList();
    }

    @Override
    public AgentSkill save(AgentSkill agentSkill) {
        AgentSkillJpaEntity entity;
        if (agentSkill.id() != null) {
            entity = repository.findById(agentSkill.id())
                    .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + agentSkill.id()));
            entity.applyLifecycle(
                    agentSkill.status(),
                    agentSkill.shadowEvaluation(),
                    agentSkill.statusReason(),
                    agentSkill.statusChangedBy(),
                    agentSkill.statusChangedAt()
            );
        } else {
            entity = mapper.toEntity(agentSkill);
        }
        
        AgentSkillJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}
