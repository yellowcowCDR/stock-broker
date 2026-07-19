package com.hermes.broker.trading.application.service;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionCommand;
import com.hermes.broker.trading.application.port.in.CreateTradingFeatureCommand;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.application.port.out.SaveTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.SaveTradingFeaturePort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingRecordCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T01:00:00Z");

    @Mock LoadTradingFeaturePort featureLoadPort;
    @Mock SaveTradingFeaturePort featureSavePort;
    @Mock LoadTradingDecisionPort decisionLoadPort;
    @Mock SaveTradingDecisionPort decisionSavePort;
    @Mock LoadAgentSkillPort skillPort;
    @Mock LoadMarketContextPort contextPort;

    private TradingRecordCommandService service;

    @BeforeEach
    void setUp() {
        service = new TradingRecordCommandService(
                featureLoadPort, featureSavePort, decisionLoadPort, decisionSavePort,
                skillPort, contextPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void brokerGeneratesFeatureIdAndUtcTimestamp() {
        when(featureLoadPort.loadByIdempotencyKey("feature-key")).thenReturn(Optional.empty());
        when(contextPort.loadById("context-1")).thenReturn(Optional.of(context()));

        TradingFeatureSnapshot result = service.createFeature(new CreateTradingFeatureCommand(
                "005930", MarketType.DOMESTIC, Map.of("rsi", 30), Map.of(),
                Map.of("marketContextId", "context-1"), "feature-key"));

        assertThat(result.featureId()).isNotBlank();
        assertThat(result.snapshotAt()).isEqualTo(NOW);
        assertThat(result.idempotencyKey()).isEqualTo("feature-key");
        verify(featureSavePort).save(result);
    }

    @Test
    void activeDecisionUsesExistingFeatureAndActiveStrategy() {
        TradingFeatureSnapshot feature = feature();
        when(decisionLoadPort.loadByIdempotencyKey("decision-key")).thenReturn(Optional.empty());
        when(featureLoadPort.loadById("feature-1")).thenReturn(Optional.of(feature));
        when(skillPort.loadByVersion(2)).thenReturn(Optional.of(skill(AgentSkillStatus.ACTIVE)));
        when(contextPort.loadById("context-1")).thenReturn(Optional.of(context()));
        when(decisionLoadPort.existsByFeatureAndStrategyAndMode(
                "feature-1", "2", TradingDecisionMode.ACTIVE)).thenReturn(false);

        TradingDecision result = service.createActiveDecision(new CreateTradingDecisionCommand(
                "feature-1", TradingDecisionType.BUY, 2, "signal",
                new BigDecimal("70000"), BigDecimal.ONE, "decision-key"));

        assertThat(result.mode()).isEqualTo(TradingDecisionMode.ACTIVE);
        assertThat(result.strategyVersion()).isEqualTo("2");
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.decidedAt()).isEqualTo(NOW);
        verify(decisionSavePort).save(result);
    }

    @Test
    void shadowDecisionRequiresShadowStrategy() {
        when(decisionLoadPort.loadByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(featureLoadPort.loadById("feature-1")).thenReturn(Optional.of(feature()));
        when(skillPort.loadByVersion(2)).thenReturn(Optional.of(skill(AgentSkillStatus.CANDIDATE)));

        assertThatThrownBy(() -> service.createShadowDecision(new CreateTradingDecisionCommand(
                "feature-1", TradingDecisionType.HOLD, 2, "wait",
                null, null, "shadow-key")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be SHADOW");
    }

    private TradingFeatureSnapshot feature() {
        return new TradingFeatureSnapshot(
                "feature-1", "005930", MarketType.DOMESTIC,
                Map.of(), Map.of(), Map.of("marketContextId", "context-1"),
                NOW.minusSeconds(60), "feature-key");
    }

    private AgentSkill skill(AgentSkillStatus status) {
        return new AgentSkill(
                2L, NOW.minusSeconds(3600), NOW.minusSeconds(60), "strategy",
                status, Map.of("rsi", 30), 2, 1, Map.of(), "test", "hermes");
    }

    private MarketContext context() {
        MarketOverview overview = new MarketOverview(
                MarketType.DOMESTIC, List.of(), 1, 0, 0, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "KIS", "KIS", NOW.minusSeconds(60), NOW.plusSeconds(300), true, "FRESH");
        return new MarketContext(
                "context-1", MarketType.DOMESTIC, MarketEntryPolicy.ALLOW_NEW_ENTRIES,
                BigDecimal.ONE, overview, List.of("ok"), "hermes", "correlation-1",
                NOW.minusSeconds(60), NOW.plusSeconds(300));
    }
}
