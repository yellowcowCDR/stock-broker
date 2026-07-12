package com.hermes.broker.agent.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentSkillJpaRepository extends JpaRepository<AgentSkillJpaEntity, Long> {
    
    Optional<AgentSkillJpaEntity> findFirstByIsActiveTrueOrderByVersionDesc();
}
