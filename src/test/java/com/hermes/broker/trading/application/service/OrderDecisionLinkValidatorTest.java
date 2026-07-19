package com.hermes.broker.trading.application.service;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import com.hermes.broker.trading.dto.OrderRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDecisionLinkValidatorTest {

    @Mock LoadTradingDecisionPort decisionPort;
    @Mock LoadTradingFeaturePort featurePort;
    @Mock LoadAgentSkillPort skillPort;

    private OrderDecisionLinkValidator validator;
    private TradingFeatureSnapshot feature;

    @BeforeEach
    void setUp() {
        validator = new OrderDecisionLinkValidator(decisionPort, featurePort, skillPort);
        feature = new TradingFeatureSnapshot(
                "feature-1", "005930", MarketType.DOMESTIC,
                Map.of(), Map.of(), Map.of("marketContextId", "context-1"),
                Instant.parse("2026-07-19T00:00:00Z"));
    }

    @Test
    void acceptsOnlyMatchingPersistedActiveDecision() {
        when(decisionPort.loadById("decision-1")).thenReturn(Optional.of(decision(TradingDecisionMode.ACTIVE)));
        when(featurePort.loadById("feature-1")).thenReturn(Optional.of(feature));
        when(skillPort.loadActiveSkill()).thenReturn(Optional.of(skill(2, AgentSkillStatus.ACTIVE)));

        ValidatedOrderDecision result = validator.validate(request());

        assertThat(result.decision().decisionId()).isEqualTo("decision-1");
        assertThat(result.feature().featureId()).isEqualTo("feature-1");
    }

    @Test
    void blocksShadowDecisionBeforeOrderSubmission() {
        when(decisionPort.loadById("decision-1")).thenReturn(Optional.of(decision(TradingDecisionMode.SHADOW)));
        when(featurePort.loadById("feature-1")).thenReturn(Optional.of(feature));

        assertThatThrownBy(() -> validator.validate(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHADOW decisions can never");
    }

    @Test
    void blocksStaleStrategyVersion() {
        when(decisionPort.loadById("decision-1")).thenReturn(Optional.of(decision(TradingDecisionMode.ACTIVE)));
        when(featurePort.loadById("feature-1")).thenReturn(Optional.of(feature));
        when(skillPort.loadActiveSkill()).thenReturn(Optional.of(skill(3, AgentSkillStatus.ACTIVE)));

        assertThatThrownBy(() -> validator.validate(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer ACTIVE");
    }

    private TradingDecision decision(TradingDecisionMode mode) {
        return new TradingDecision(
                "decision-1", "feature-1", "005930", TradingDecisionType.BUY,
                "2", "buy", new BigDecimal("70000"), BigDecimal.ONE,
                Instant.parse("2026-07-19T00:01:00Z"), mode, "decision-key");
    }

    private OrderRequestDto request() {
        return OrderRequestDto.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .orderType(OrderType.BUY)
                .price(new BigDecimal("70000"))
                .quantity(1)
                .idempotencyKey("order-key")
                .decisionId("decision-1")
                .featureId("feature-1")
                .strategyVersion("2")
                .build();
    }

    private AgentSkill skill(int version, AgentSkillStatus status) {
        return new AgentSkill(
                1L, Instant.EPOCH, Instant.EPOCH, "strategy", status,
                Map.of("rsi", 30), version, null, Map.of(), "test", "broker");
    }
}
