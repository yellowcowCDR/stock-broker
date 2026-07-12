package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.domain.AgentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentSkillJpaRepository extends JpaRepository<AgentSkill, Long> {
    
    // 현재 활성화된 가장 최신 버전의 스킬 조회
    Optional<AgentSkill> findFirstByIsActiveTrueOrderByVersionDesc();
}
