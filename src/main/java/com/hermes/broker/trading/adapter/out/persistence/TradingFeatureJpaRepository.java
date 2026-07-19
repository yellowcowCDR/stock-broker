package com.hermes.broker.trading.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hermes.broker.trading.domain.MarketType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradingFeatureJpaRepository extends JpaRepository<TradingFeatureJpaEntity, String> {
    Optional<TradingFeatureJpaEntity> findFirstByStockCodeAndMarketTypeOrderBySnapshotAtDesc(
            String stockCode, MarketType marketType);

    Optional<TradingFeatureJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<TradingFeatureJpaEntity> findAllBySnapshotAtGreaterThanEqualAndSnapshotAtLessThanOrderBySnapshotAtAsc(
            Instant startInclusive, Instant endExclusive);
}
