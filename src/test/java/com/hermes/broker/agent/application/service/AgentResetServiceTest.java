package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.adapter.out.persistence.AgentSkillJpaRepository;
import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.summary.adapter.out.persistence.DailySummaryJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.TradingLogJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.TradingDecisionJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.TradingFeatureJpaRepository;
import com.hermes.broker.trading.adapter.out.persistence.ShadowPerformanceSampleJpaRepository;
import com.hermes.broker.summary.adapter.out.persistence.TradingReflectionJpaRepository;
import com.hermes.broker.agent.adapter.out.persistence.AgentSkillPerformanceJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgentResetServiceTest {

    @Mock TradingLogJpaRepository tradingLogRepository;
    @Mock DailySummaryJpaRepository dailySummaryRepository;
    @Mock TradingReflectionJpaRepository tradingReflectionRepository;
    @Mock ShadowPerformanceSampleJpaRepository shadowPerformanceSampleRepository;
    @Mock TradingDecisionJpaRepository tradingDecisionRepository;
    @Mock TradingFeatureJpaRepository tradingFeatureRepository;
    @Mock AgentSkillPerformanceJpaRepository agentSkillPerformanceRepository;
    @Mock AgentSkillJpaRepository agentSkillRepository;

    @Test
    void liveModeBlocksResetBeforeAnyDeletion() {
        TradingProperties live = new TradingProperties(
                null, "LIVE", AutonomyMode.ANALYSIS_ONLY,
                null, null, null);
        AgentResetService service = new AgentResetService(
                tradingLogRepository, dailySummaryRepository, tradingReflectionRepository,
                shadowPerformanceSampleRepository, tradingDecisionRepository,
                tradingFeatureRepository, agentSkillPerformanceRepository,
                agentSkillRepository, live);

        assertThatThrownBy(() -> service.resetLearningData(
                "operator", "test", "corr-1", "RESET_PAPER_LEARNING_DATA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAPER mode");

        verifyNoInteractions(tradingLogRepository, dailySummaryRepository,
                tradingReflectionRepository, shadowPerformanceSampleRepository,
                tradingDecisionRepository, tradingFeatureRepository,
                agentSkillPerformanceRepository, agentSkillRepository);
    }
}
