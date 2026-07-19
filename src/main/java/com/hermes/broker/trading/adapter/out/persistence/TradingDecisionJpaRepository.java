package com.hermes.broker.trading.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;

@Repository
public interface TradingDecisionJpaRepository extends JpaRepository<TradingDecisionJpaEntity, String> {
    Optional<TradingDecisionJpaEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByFeatureIdAndStrategyVersionAndMode(
            String featureId, String strategyVersion, TradingDecisionMode mode);

    List<TradingDecisionJpaEntity> findAllByDecidedAtGreaterThanEqualAndDecidedAtLessThanOrderByDecidedAtAsc(
            Instant startInclusive, Instant endExclusive);
}
