package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPerformancePort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPerformancePort;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.summary.application.port.out.LoadTradingReflectionPort;
import com.hermes.broker.summary.domain.TradeReview;
import com.hermes.broker.summary.domain.TradingReflection;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.application.port.out.LoadShadowPerformanceSamplePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyPerformanceServiceTest {

    @Mock LoadAgentSkillPerformancePort loadPerformancePort;
    @Mock SaveAgentSkillPerformancePort savePerformancePort;
    @Mock LoadTradingReflectionPort loadReflectionPort;
    @Mock LoadShadowPerformanceSamplePort loadShadowPerformanceSamplePort;
    @Mock LoadAgentSkillPort loadAgentSkillPort;

    @Test
    void aggregatesOnlyCompleteBrokerReflectionsWithoutInventingTradeWinRate() {
        StrategyPerformanceService service = new StrategyPerformanceService(
                loadPerformancePort, savePerformancePort, loadReflectionPort,
                loadShadowPerformanceSamplePort, loadAgentSkillPort);
        when(loadReflectionPort.loadCompleteByStrategyVersion("strategy-v1"))
                .thenReturn(List.of(reflection("2026-07-17", "1.0"),
                        reflection("2026-07-18", "-0.5")));

        AgentSkillPerformance result = service.evaluate("strategy-v1");

        assertThat(result.tradeCount()).isEqualTo(2);
        assertThat(result.evaluationDays()).isEqualTo(2);
        assertThat(result.winRate()).isNull();
        assertThat(result.totalReturnRate()).isEqualByComparingTo("0.495000");
        assertThat(result.profitFactor()).isEqualByComparingTo("2.000000");
        assertThat(result.holdAccuracy()).isNull();
        assertThat(result.riskBlockEffect()).isNull();
        ArgumentCaptor<AgentSkillPerformance> saved = ArgumentCaptor.forClass(AgentSkillPerformance.class);
        verify(savePerformancePort).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(result);
    }

    @Test
    void refusesSyntheticPerformanceWhenNoCompleteReflectionExists() {
        StrategyPerformanceService service = new StrategyPerformanceService(
                loadPerformancePort, savePerformancePort, loadReflectionPort,
                loadShadowPerformanceSamplePort, loadAgentSkillPort);
        when(loadReflectionPort.loadCompleteByStrategyVersion("strategy-v1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.evaluate("strategy-v1"))
                .isInstanceOf(DataPipelineUnavailableException.class)
                .hasMessageContaining("synthetic metrics are disabled");
    }

    @Test
    void aggregatesCompletedRealQuoteShadowSamplesWhenNoActiveReflectionExists() {
        StrategyPerformanceService service = new StrategyPerformanceService(
                loadPerformancePort, savePerformancePort, loadReflectionPort,
                loadShadowPerformanceSamplePort, loadAgentSkillPort);
        when(loadReflectionPort.loadCompleteByStrategyVersion("2")).thenReturn(List.of());
        when(loadAgentSkillPort.loadByVersion(2)).thenReturn(Optional.of(new AgentSkill(
                2L, Instant.EPOCH, Instant.EPOCH, "shadow", AgentSkillStatus.SHADOW,
                Map.of(), 2, 1, Map.of(), "test", "broker")));
        when(loadShadowPerformanceSamplePort.loadByStrategyVersion(
                "2", ShadowSampleStatus.COMPLETED)).thenReturn(List.of(
                shadowSample("sample-1", "2026-07-17", TradingDecisionType.BUY, "2.0", "2.0"),
                shadowSample("sample-2", "2026-07-18", TradingDecisionType.SELL, "1.0", "-1.0"),
                shadowSample("sample-3", "2026-07-18", TradingDecisionType.BLOCK, "-3.0", "0.0")
        ));

        AgentSkillPerformance result = service.evaluate("2");

        assertThat(result.tradeCount()).isEqualTo(2);
        assertThat(result.evaluationDays()).isEqualTo(2);
        assertThat(result.winRate()).isEqualByComparingTo("50.000000");
        assertThat(result.riskBlockEffect()).isEqualByComparingTo("3.000000");
        verify(savePerformancePort).save(result);
    }

    private TradingReflection reflection(String date, String dailyReturn) {
        TradeReview review = new TradeReview(
                "005930", MarketType.DOMESTIC, "decision-1", "feature-1", "context-1",
                "strategy-v1", "BUY", "EXECUTED", new BigDecimal("70000"),
                new BigDecimal("70100"), BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("100"), "KRW", "KIS", new BigDecimal("100"),
                Instant.parse(date + "T00:00:00Z"), Instant.parse(date + "T00:01:00Z"),
                Instant.parse(date + "T00:02:00Z"), true, "test");
        return new TradingReflection(
                "reflection-" + date, LocalDate.parse(date), MarketType.DOMESTIC,
                "Asia/Seoul", "strategy-v1", new BigDecimal(dailyReturn),
                new BigDecimal("0.1"), new BigDecimal("100"), new BigDecimal("100"),
                1, 0, 0, true, List.of(review), "feedback", "plan", Instant.now());
    }

    private ShadowPerformanceSample shadowSample(
            String id, String date, TradingDecisionType type,
            String rawReturn, String actionReturn) {
        Instant startedAt = Instant.parse(date + "T01:00:00Z");
        return new ShadowPerformanceSample(
                id, "decision-" + id, "feature-" + id, "2", "005930",
                MarketType.DOMESTIC, null, type, new BigDecimal("100"),
                new BigDecimal("102"), new BigDecimal(rawReturn),
                new BigDecimal(actionReturn), LocalDate.parse(date),
                ShadowSampleStatus.COMPLETED, "KIS_QUOTE_API", startedAt,
                startedAt.plusSeconds(3600));
    }
}
