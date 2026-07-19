package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPerformancePort;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.property.StrategyEvaluationProperties;
import com.hermes.broker.common.property.TradingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AgentSkillLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    private LoadAgentSkillPort loadSkillPort;
    private SaveAgentSkillPort saveSkillPort;
    private LoadAgentSkillPerformancePort loadPerformancePort;
    private AgentSkillLifecycleService service;

    @BeforeEach
    void setUp() {
        loadSkillPort = mock(LoadAgentSkillPort.class);
        saveSkillPort = mock(SaveAgentSkillPort.class);
        loadPerformancePort = mock(LoadAgentSkillPerformancePort.class);
        StrategyEvaluationProperties properties = new StrategyEvaluationProperties(
                20,
                10,
                2,
                true,
                10,
                new BigDecimal("0.30"),
                new BigDecimal("0.20")
        );
        service = new AgentSkillLifecycleService(
                loadSkillPort,
                saveSkillPort,
                loadPerformancePort,
                properties,
                new TradingProperties(null, "PAPER", null, null, null, null),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        given(saveSkillPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shadowEvaluationFailsClosedWithoutBrokerPerformance() {
        AgentSkill shadow = candidate().startShadow("hermes", "start", NOW.minusSeconds(60));
        given(loadSkillPort.loadByVersion(2)).willReturn(Optional.of(shadow));
        given(loadPerformancePort.loadPerformance("2")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluateShadow(2, "evaluator", "evaluate"))
                .isInstanceOf(DataPipelineUnavailableException.class)
                .hasMessageContaining("Promotion remains blocked");
    }

    @Test
    void promotionRequiresEligibleShadowEvaluation() {
        AgentSkill shadow = candidate().startShadow("hermes", "start", NOW.minusSeconds(60));
        given(loadSkillPort.loadByVersion(2)).willReturn(Optional.of(shadow));

        assertThatThrownBy(() -> service.promote(2, "human", "approve"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no eligible Broker-generated shadow evaluation");
    }

    @Test
    void eligibleRealPerformanceCanBeExplicitlyPromoted() {
        AgentSkill active = AgentSkill.createInitial("active", Map.of("rsi", 30), NOW.minusSeconds(3600));
        AgentSkill shadow = candidate().startShadow("hermes", "start", NOW.minusSeconds(1800));
        given(loadSkillPort.loadByVersion(2)).willReturn(Optional.of(shadow));
        given(loadSkillPort.loadActiveSkill()).willReturn(Optional.of(active));
        given(loadPerformancePort.loadPerformance("2")).willReturn(Optional.of(performance()));

        AgentSkill evaluated = service.evaluateShadow(2, "broker", "real sample");
        given(loadSkillPort.loadByVersion(2)).willReturn(Optional.of(evaluated));
        AgentSkill promoted = service.promote(2, "human-reviewer", "paper approval");

        assertThat(evaluated.shadowEvaluation())
                .containsEntry("eligibleForPromotion", true)
                .containsEntry("tradeCount", 25);
        assertThat(promoted.status()).isEqualTo(AgentSkillStatus.ACTIVE);
        assertThat(promoted.statusChangedBy()).isEqualTo("human-reviewer");

        ArgumentCaptor<AgentSkill> captor = ArgumentCaptor.forClass(AgentSkill.class);
        verify(saveSkillPort, times(3)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(AgentSkillStatus.ROLLED_BACK);
        assertThat(captor.getAllValues().get(2).status()).isEqualTo(AgentSkillStatus.ACTIVE);
    }

    @Test
    void insufficientSampleIsRecordedButCannotBePromoted() {
        AgentSkill shadow = candidate().startShadow("hermes", "start", NOW.minusSeconds(60));
        given(loadSkillPort.loadByVersion(2)).willReturn(Optional.of(shadow));
        AgentSkillPerformance insufficient = new AgentSkillPerformance(
                "2", 5, 2, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, NOW
        );
        given(loadPerformancePort.loadPerformance("2")).willReturn(Optional.of(insufficient));

        AgentSkill evaluated = service.evaluateShadow(2, "broker", "sample check");
        given(loadSkillPort.loadByVersion(2)).willReturn(Optional.of(evaluated));

        assertThat(evaluated.shadowEvaluation()).containsEntry("eligibleForPromotion", false);
        assertThatThrownBy(() -> service.promote(2, "human", "approve"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rollbackRequiresAnOlderPreviouslyActiveVersion() {
        AgentSkill old = AgentSkill.createInitial(
                "old", Map.of("rsi", 30), NOW.minusSeconds(7200))
                .transitionTo(AgentSkillStatus.ROLLED_BACK, "human", "superseded", NOW.minusSeconds(3600));
        AgentSkill currentBase = AgentSkill.createCandidate(
                old, old.transitionTo(AgentSkillStatus.ACTIVE, "human", "restore", NOW.minusSeconds(7000)),
                "current", Map.of("rsi", 28), "hermes", NOW.minusSeconds(3600));
        AgentSkill current = currentBase.transitionTo(
                AgentSkillStatus.ACTIVE, "human", "promoted", NOW.minusSeconds(1800));
        given(loadSkillPort.loadByVersion(1)).willReturn(Optional.of(old));
        given(loadSkillPort.loadActiveSkill()).willReturn(Optional.of(current));

        AgentSkill restored = service.rollback(1, "human-reviewer", "degradation confirmed");

        assertThat(restored.status()).isEqualTo(AgentSkillStatus.ACTIVE);
        assertThat(restored.version()).isEqualTo(1);
    }

    private AgentSkill candidate() {
        AgentSkill active = AgentSkill.createInitial("active", Map.of("rsi", 30), NOW.minusSeconds(3600));
        return AgentSkill.createCandidate(
                active, active, "candidate", Map.of("rsi", 28), "hermes", NOW.minusSeconds(1800));
    }

    private AgentSkillPerformance performance() {
        return new AgentSkillPerformance(
                "2",
                25,
                12,
                new BigDecimal("0.60"),
                new BigDecimal("0.08"),
                new BigDecimal("0.003"),
                new BigDecimal("100"),
                new BigDecimal("50"),
                new BigDecimal("2.0"),
                new BigDecimal("1.8"),
                new BigDecimal("1.2"),
                new BigDecimal("0.05"),
                new BigDecimal("0.70"),
                new BigDecimal("0.90"),
                NOW.minusSeconds(30)
        );
    }
}
