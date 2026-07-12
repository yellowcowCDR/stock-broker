package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.adapter.out.persistence.AgentSkillJpaEntity;
import com.hermes.broker.agent.adapter.out.persistence.AgentSkillJpaRepository;
import com.hermes.broker.summary.adapter.out.persistence.DailySummaryJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.TradingLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentResetService {

    private final TradingLogJpaRepository tradingLogRepository;
    private final DailySummaryJpaRepository dailySummaryRepository;
    private final AgentSkillJpaRepository agentSkillRepository;

    @Transactional
    public void resetLearningData() {
        // 1. 매매 일지 및 회고 데이터 삭제
        tradingLogRepository.deleteAllInBatch();
        dailySummaryRepository.deleteAllInBatch();

        // 2. 스킬 초기화 (기존 스킬 비활성화 후 v1 생성)
        agentSkillRepository.findAll().forEach(AgentSkillJpaEntity::deactivate);
        
        AgentSkillJpaEntity initialSkill = AgentSkillJpaEntity.builder()
                .version(1)
                .description("Initial default skill after reset")
                .isActive(true)
                .skillParameters(new HashMap<>())
                .build();
                
        agentSkillRepository.save(initialSkill);
        
        log.info("Agent learning data has been completely reset to initial state.");
    }
}
