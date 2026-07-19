package com.hermes.broker.agent.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface AgentSkillJpaRepository extends JpaRepository<AgentSkillJpaEntity, Long> {
    
    Optional<AgentSkillJpaEntity> findFirstByIsActiveTrueOrderByVersionDesc();

    Optional<AgentSkillJpaEntity> findFirstByOrderByVersionDesc();

    Optional<AgentSkillJpaEntity> findByVersion(Integer version);

    List<AgentSkillJpaEntity> findAllByOrderByVersionDesc();
}
