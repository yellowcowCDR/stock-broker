package com.hermes.broker.agent.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentSkillPerformanceJpaRepository extends JpaRepository<AgentSkillPerformanceJpaEntity, String> {
}
