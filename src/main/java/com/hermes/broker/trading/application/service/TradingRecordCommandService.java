package com.hermes.broker.trading.application.service;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionCommand;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.CreateTradingFeatureCommand;
import com.hermes.broker.trading.application.port.in.CreateTradingFeatureUseCase;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.application.port.out.SaveTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.SaveTradingFeaturePort;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingRecordCommandService
        implements CreateTradingFeatureUseCase, CreateTradingDecisionUseCase {

    private final LoadTradingFeaturePort loadTradingFeaturePort;
    private final SaveTradingFeaturePort saveTradingFeaturePort;
    private final LoadTradingDecisionPort loadTradingDecisionPort;
    private final SaveTradingDecisionPort saveTradingDecisionPort;
    private final LoadAgentSkillPort loadAgentSkillPort;
    private final LoadMarketContextPort loadMarketContextPort;
    private final Clock clock;

    @Override
    @Transactional
    public TradingFeatureSnapshot createFeature(CreateTradingFeatureCommand command) {
        validateFeatureCommand(command);
        String stockCode = normalizeStockCode(command.stockCode());
        Map<String, Object> technical = safeMap(command.technicalFeatures());
        Map<String, Object> news = safeMap(command.newsFeatures());
        Map<String, Object> risk = safeMap(command.riskFeatures());

        var replay = loadTradingFeaturePort.loadByIdempotencyKey(command.idempotencyKey());
        if (replay.isPresent()) {
            assertSameFeature(replay.get(), stockCode, command, technical, news, risk);
            return replay.get();
        }

        Instant now = clock.instant();
        validateMarketContextIfPresent(risk, command.marketType(), now);
        TradingFeatureSnapshot snapshot = new TradingFeatureSnapshot(
                UUID.randomUUID().toString(), stockCode, command.marketType(),
                technical, news, risk, now, command.idempotencyKey().trim());
        saveTradingFeaturePort.save(snapshot);
        return snapshot;
    }

    @Override
    @Transactional
    public TradingDecision createActiveDecision(CreateTradingDecisionCommand command) {
        return createDecision(command, TradingDecisionMode.ACTIVE, AgentSkillStatus.ACTIVE);
    }

    @Override
    @Transactional
    public TradingDecision createShadowDecision(CreateTradingDecisionCommand command) {
        return createDecision(command, TradingDecisionMode.SHADOW, AgentSkillStatus.SHADOW);
    }

    private TradingDecision createDecision(CreateTradingDecisionCommand command,
                                           TradingDecisionMode mode,
                                           AgentSkillStatus requiredStatus) {
        validateDecisionCommand(command);
        var replay = loadTradingDecisionPort.loadByIdempotencyKey(command.idempotencyKey());
        if (replay.isPresent()) {
            assertSameDecision(replay.get(), command, mode);
            return replay.get();
        }

        TradingFeatureSnapshot feature = loadTradingFeaturePort.loadById(command.featureId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Feature " + command.featureId() + " does not exist."));
        AgentSkill skill = loadAgentSkillPort.loadByVersion(command.strategyVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Strategy version " + command.strategyVersion() + " does not exist."));
        if (skill.status() != requiredStatus) {
            throw new IllegalStateException("Strategy version " + command.strategyVersion()
                    + " must be " + requiredStatus + " for a " + mode + " decision; current status is "
                    + skill.status() + ".");
        }
        String version = Integer.toString(command.strategyVersion());
        if (loadTradingDecisionPort.existsByFeatureAndStrategyAndMode(
                feature.featureId(), version, mode)) {
            throw new IllegalStateException("A " + mode + " decision already exists for feature "
                    + feature.featureId() + " and strategy version " + version + ".");
        }

        Instant now = clock.instant();
        if (feature.snapshotAt() == null || feature.snapshotAt().isAfter(now)) {
            throw new IllegalStateException("Feature " + feature.featureId()
                    + " was not available at decision time.");
        }
        if (command.decisionType() == TradingDecisionType.BUY) {
            validateRequiredMarketContext(feature.riskFeatures(), feature.marketType(), now);
        }
        TradingDecision decision = new TradingDecision(
                UUID.randomUUID().toString(), feature.featureId(), feature.stockCode(),
                command.decisionType(), version, command.reason().trim(),
                command.recommendedPrice(), command.recommendedQuantity(), now,
                mode, command.idempotencyKey().trim());
        saveTradingDecisionPort.save(decision);
        return decision;
    }

    private void validateFeatureCommand(CreateTradingFeatureCommand command) {
        if (command == null || command.marketType() == null
                || command.stockCode() == null || command.stockCode().isBlank()
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException(
                    "stockCode, marketType and idempotencyKey are required.");
        }
        if (command.stockCode().trim().length() > 20 || command.idempotencyKey().trim().length() > 160) {
            throw new IllegalArgumentException("stockCode or idempotencyKey exceeds its maximum length.");
        }
    }

    private void validateDecisionCommand(CreateTradingDecisionCommand command) {
        if (command == null || command.featureId() == null || command.featureId().isBlank()
                || command.decisionType() == null || command.strategyVersion() <= 0
                || command.reason() == null || command.reason().isBlank()
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException(
                    "featureId, decisionType, positive strategyVersion, reason and idempotencyKey are required.");
        }
        if (command.featureId().length() > 36 || command.reason().length() > 1000
                || command.idempotencyKey().length() > 160) {
            throw new IllegalArgumentException("Decision field exceeds its maximum length.");
        }
        boolean orderDecision = command.decisionType() == TradingDecisionType.BUY
                || command.decisionType() == TradingDecisionType.SELL;
        if (orderDecision && (!positive(command.recommendedPrice())
                || !positive(command.recommendedQuantity()))) {
            throw new IllegalArgumentException(
                    "BUY/SELL decisions require positive recommendedPrice and recommendedQuantity.");
        }
        if (orderDecision && (command.recommendedPrice().stripTrailingZeros().scale() > 4
                || command.recommendedQuantity().stripTrailingZeros().scale() > 0
                || command.recommendedQuantity().compareTo(
                        BigDecimal.valueOf(Integer.MAX_VALUE)) > 0)) {
            throw new IllegalArgumentException(
                    "recommendedPrice supports at most 4 decimals and recommendedQuantity must be an integer order quantity.");
        }
        if (!orderDecision && (command.recommendedPrice() != null
                || command.recommendedQuantity() != null)) {
            throw new IllegalArgumentException(
                    "HOLD/BLOCK decisions must not include recommendedPrice or recommendedQuantity.");
        }
    }

    private void validateMarketContextIfPresent(Map<String, Object> riskFeatures,
                                                com.hermes.broker.trading.domain.MarketType marketType,
                                                Instant now) {
        Object rawContextId = riskFeatures.get("marketContextId");
        if (rawContextId == null || rawContextId.toString().isBlank()) {
            return;
        }
        validateMarketContext(rawContextId, marketType, now);
    }

    private void validateRequiredMarketContext(Map<String, Object> riskFeatures,
                                               com.hermes.broker.trading.domain.MarketType marketType,
                                               Instant now) {
        Object rawContextId = riskFeatures == null ? null : riskFeatures.get("marketContextId");
        if (rawContextId == null || rawContextId.toString().isBlank()) {
            throw new IllegalStateException(
                    "BUY decisions require a fresh riskFeatures.marketContextId.");
        }
        validateMarketContext(rawContextId, marketType, now);
    }

    private void validateMarketContext(Object rawContextId,
                                       com.hermes.broker.trading.domain.MarketType marketType,
                                       Instant now) {
        MarketContext context = loadMarketContextPort.loadById(rawContextId.toString())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Market context " + rawContextId + " does not exist."));
        if (context.marketType() != marketType || context.overviewSnapshot() == null
                || context.overviewSnapshot().marketType() != marketType
                || !context.overviewSnapshot().complete()) {
            throw new IllegalStateException("Market context " + rawContextId
                    + " is incomplete or belongs to another market.");
        }
        if (context.analyzedAt() == null || context.analyzedAt().isAfter(now)
                || context.validUntil() == null || !now.isBefore(context.validUntil())
                || context.overviewSnapshot().fetchedAt() == null
                || context.overviewSnapshot().fetchedAt().isAfter(now)
                || context.overviewSnapshot().validUntil() == null
                || !now.isBefore(context.overviewSnapshot().validUntil())) {
            throw new IllegalStateException("Market context " + rawContextId
                    + " is stale or was not available at feature creation time.");
        }
    }

    private void assertSameFeature(TradingFeatureSnapshot existing, String stockCode,
                                   CreateTradingFeatureCommand command,
                                   Map<String, Object> technical, Map<String, Object> news,
                                   Map<String, Object> risk) {
        if (!Objects.equals(existing.stockCode(), stockCode)
                || existing.marketType() != command.marketType()
                || !Objects.equals(existing.technicalFeatures(), technical)
                || !Objects.equals(existing.newsFeatures(), news)
                || !Objects.equals(existing.riskFeatures(), risk)) {
            throw new IllegalArgumentException(
                    "Feature idempotency key was already used with a different payload.");
        }
    }

    private void assertSameDecision(TradingDecision existing,
                                    CreateTradingDecisionCommand command,
                                    TradingDecisionMode mode) {
        if (!Objects.equals(existing.featureId(), command.featureId())
                || existing.decisionType() != command.decisionType()
                || !Objects.equals(existing.strategyVersion(), Integer.toString(command.strategyVersion()))
                || !Objects.equals(existing.reason(), command.reason().trim())
                || !sameNumber(existing.recommendedPrice(), command.recommendedPrice())
                || !sameNumber(existing.recommendedQuantity(), command.recommendedQuantity())
                || existing.mode() != mode) {
            throw new IllegalArgumentException(
                    "Decision idempotency key was already used with a different payload.");
        }
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean sameNumber(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private String normalizeStockCode(String value) {
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
