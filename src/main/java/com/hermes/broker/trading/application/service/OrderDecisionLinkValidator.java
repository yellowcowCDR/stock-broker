package com.hermes.broker.trading.application.service;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import com.hermes.broker.trading.dto.OrderRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderDecisionLinkValidator {

    private final LoadTradingDecisionPort loadTradingDecisionPort;
    private final LoadTradingFeaturePort loadTradingFeaturePort;
    private final LoadAgentSkillPort loadAgentSkillPort;

    public ValidatedOrderDecision validate(OrderRequestDto request) {
        requireLinkage(request);
        TradingDecision decision = loadTradingDecisionPort.loadById(request.getDecisionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Decision " + request.getDecisionId() + " does not exist."));
        TradingFeatureSnapshot feature = loadTradingFeaturePort.loadById(request.getFeatureId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Feature " + request.getFeatureId() + " does not exist."));

        if (decision.mode() != TradingDecisionMode.ACTIVE) {
            throw new IllegalStateException("SHADOW decisions can never be submitted as orders.");
        }
        if (decision.decisionType() != TradingDecisionType.BUY
                && decision.decisionType() != TradingDecisionType.SELL) {
            throw new IllegalStateException("Only BUY/SELL decisions can enter the order pipeline.");
        }
        if (!Objects.equals(decision.featureId(), feature.featureId())
                || !Objects.equals(decision.featureId(), request.getFeatureId())
                || !Objects.equals(decision.stockCode(), normalize(request.getStockCode()))
                || !Objects.equals(feature.stockCode(), normalize(request.getStockCode()))
                || feature.marketType() != request.getMarketType()
                || !Objects.equals(decision.strategyVersion(), request.getStrategyVersion())) {
            throw new IllegalArgumentException(
                    "Order decision/feature/stock/market/strategy linkage does not match Broker DB.");
        }
        TradingDecisionType expectedType = request.getOrderType() == OrderType.BUY
                ? TradingDecisionType.BUY : TradingDecisionType.SELL;
        if (decision.decisionType() != expectedType
                || !sameNumber(decision.recommendedPrice(), request.getPrice())
                || !sameNumber(decision.recommendedQuantity(),
                        BigDecimal.valueOf(request.getQuantity()))) {
            throw new IllegalArgumentException(
                    "Order side, price or quantity does not match the persisted decision.");
        }
        AgentSkill active = loadAgentSkillPort.loadActiveSkill()
                .orElseThrow(() -> new IllegalStateException("No ACTIVE strategy exists."));
        if (active.status() != AgentSkillStatus.ACTIVE
                || !Objects.equals(Integer.toString(active.version()), decision.strategyVersion())) {
            throw new IllegalStateException("Decision strategy version " + decision.strategyVersion()
                    + " is no longer ACTIVE; stale decisions cannot be ordered.");
        }
        if (feature.snapshotAt() == null || decision.decidedAt() == null
                || feature.snapshotAt().isAfter(decision.decidedAt())) {
            throw new IllegalStateException("Feature was not available at decision time.");
        }
        return new ValidatedOrderDecision(decision, feature);
    }

    private void requireLinkage(OrderRequestDto request) {
        if (request.getDecisionId() == null || request.getDecisionId().isBlank()
                || request.getFeatureId() == null || request.getFeatureId().isBlank()
                || request.getStrategyVersion() == null || request.getStrategyVersion().isBlank()) {
            throw new IllegalArgumentException(
                    "decisionId, featureId and strategyVersion are required for every order.");
        }
    }

    private boolean sameNumber(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
