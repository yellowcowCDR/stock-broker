package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.port.out.SaveTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TradingDecisionPersistenceAdapter implements SaveTradingDecisionPort, LoadTradingDecisionPort {

    private final TradingDecisionJpaRepository repository;

    @Override
    public void save(TradingDecision decision) {
        TradingDecisionJpaEntity entity = new TradingDecisionJpaEntity();
        if (decision.decisionId() != null) {
            entity.setDecisionId(decision.decisionId());
        }
        entity.setFeatureId(decision.featureId());
        entity.setStockCode(decision.stockCode());
        entity.setDecisionType(decision.decisionType());
        entity.setStrategyVersion(decision.strategyVersion());
        entity.setReason(decision.reason());
        entity.setRecommendedPrice(decision.recommendedPrice());
        entity.setRecommendedQuantity(decision.recommendedQuantity());
        entity.setDecidedAt(decision.decidedAt());
        entity.setMode(decision.mode());
        entity.setIdempotencyKey(decision.idempotencyKey());

        repository.save(entity);
    }

    @Override
    public java.util.Optional<TradingDecision> loadById(String decisionId) {
        return repository.findById(decisionId).map(this::toDomain);
    }

    @Override
    public java.util.Optional<TradingDecision> loadByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public boolean existsByFeatureAndStrategyAndMode(
            String featureId, String strategyVersion,
            com.hermes.broker.trading.domain.decision.TradingDecisionMode mode) {
        return repository.existsByFeatureIdAndStrategyVersionAndMode(
                featureId, strategyVersion, mode);
    }

    @Override
    public List<TradingDecision> loadBetween(Instant startInclusive, Instant endExclusive) {
        return repository
                .findAllByDecidedAtGreaterThanEqualAndDecidedAtLessThanOrderByDecidedAtAsc(
                        startInclusive, endExclusive)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private TradingDecision toDomain(TradingDecisionJpaEntity entity) {
        return new TradingDecision(
                entity.getDecisionId(),
                entity.getFeatureId(),
                entity.getStockCode(),
                entity.getDecisionType(),
                entity.getStrategyVersion(),
                entity.getReason(),
                entity.getRecommendedPrice(),
                entity.getRecommendedQuantity(),
                entity.getDecidedAt(),
                entity.getMode(),
                entity.getIdempotencyKey()
        );
    }
}
