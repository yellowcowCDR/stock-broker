package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.adapter.out.persistence.AgentSkillJpaEntity;
import com.hermes.broker.agent.adapter.out.persistence.AgentSkillJpaRepository;
import com.hermes.broker.summary.adapter.out.persistence.DailySummaryJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.TradingLogJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.TradingDecisionJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.TradingFeatureJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.ShadowPerformanceSampleJpaRepository;
import com.hermes.broker.summary.adapter.out.persistence.TradingReflectionJpaRepository;
import com.hermes.broker.agent.adapter.out.persistence.AgentSkillPerformanceJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.common.property.TradingProperties;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentResetService {

    private final TradingLogJpaRepository tradingLogRepository;
    private final DailySummaryJpaRepository dailySummaryRepository;
    private final TradingReflectionJpaRepository tradingReflectionRepository;
    private final ShadowPerformanceSampleJpaRepository shadowPerformanceSampleRepository;
    private final TradingDecisionJpaRepository tradingDecisionRepository;
    private final TradingFeatureJpaRepository tradingFeatureRepository;
    private final AgentSkillPerformanceJpaRepository agentSkillPerformanceRepository;
    private final AgentSkillJpaRepository agentSkillRepository;
    private final TradingProperties tradingProperties;

    @Transactional
    public void resetLearningData(String actor, String reason, String correlationId,
                                  String confirmation) {
        validateReset(actor, reason, correlationId, confirmation);

        // 1. 매매 일지 및 회고 데이터 삭제
        tradingLogRepository.deleteAllInBatch();
        shadowPerformanceSampleRepository.deleteAllInBatch();
        tradingDecisionRepository.deleteAllInBatch();
        tradingFeatureRepository.deleteAllInBatch();
        tradingReflectionRepository.deleteAllInBatch();
        dailySummaryRepository.deleteAllInBatch();
        agentSkillPerformanceRepository.deleteAllInBatch();

        // 2. 활성 전략 partial unique index와 충돌하지 않도록 먼저 모두 비활성화해 flush한다.
        var skills = agentSkillRepository.findAll();
        skills.forEach(skill -> skill.applyLifecycle(
                AgentSkillStatus.ROLLED_BACK,
                skill.getShadowEvaluation(),
                "Administrative PAPER reset: " + reason,
                actor,
                Instant.now()));
        agentSkillRepository.saveAllAndFlush(skills);

        AgentSkillJpaEntity initialSkill = agentSkillRepository.findByVersion(1)
                .orElseGet(() -> AgentSkillJpaEntity.builder()
                        .version(1)
                        .description("Initial default skill after reset")
                        .isActive(false)
                        .lifecycleStatus(AgentSkillStatus.ROLLED_BACK)
                        .skillParameters(new HashMap<>())
                        .statusReason("Created during PAPER reset")
                        .statusChangedBy(actor)
                        .statusChangedAt(Instant.now())
                        .build());
        initialSkill.resetAsInitial(actor, reason);
        agentSkillRepository.save(initialSkill);

        log.warn("Agent learning data reset completed. actor={}, correlationId={}, reason={}",
                actor, correlationId, reason);
    }

    private void validateReset(String actor, String reason, String correlationId,
                               String confirmation) {
        if (!"PAPER".equalsIgnoreCase(tradingProperties.mode())) {
            throw new IllegalStateException("Agent reset is allowed only in PAPER mode.");
        }
        if (actor == null || actor.isBlank() || reason == null || reason.isBlank()
                || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("actor, reason and correlationId are required.");
        }
        if (!"RESET_PAPER_LEARNING_DATA".equals(confirmation)) {
            throw new IllegalArgumentException(
                    "confirmation must be RESET_PAPER_LEARNING_DATA.");
        }
    }
}
