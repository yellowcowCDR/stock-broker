package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.port.out.SaveTradingFeaturePort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TradingFeaturePersistenceAdapter implements SaveTradingFeaturePort, LoadTradingFeaturePort {

    private final TradingFeatureJpaRepository repository;

    @Override
    public void save(TradingFeatureSnapshot snapshot) {
        TradingFeatureJpaEntity entity = new TradingFeatureJpaEntity();
        if (snapshot.featureId() != null) {
            entity.setFeatureId(snapshot.featureId());
        }
        entity.setStockCode(snapshot.stockCode());
        entity.setMarketType(snapshot.marketType());
        entity.setTechnicalFeatures(snapshot.technicalFeatures());
        entity.setNewsFeatures(snapshot.newsFeatures());
        entity.setRiskFeatures(snapshot.riskFeatures());
        entity.setSnapshotAt(snapshot.snapshotAt());
        entity.setIdempotencyKey(snapshot.idempotencyKey());
        
        repository.save(entity);
    }

    @Override
    public Optional<TradingFeatureSnapshot> loadById(String featureId) {
        return repository.findById(featureId).map(this::toDomain);
    }

    @Override
    public Optional<TradingFeatureSnapshot> loadByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public Optional<TradingFeatureSnapshot> loadLatest(String stockCode, MarketType marketType) {
        return repository.findFirstByStockCodeAndMarketTypeOrderBySnapshotAtDesc(stockCode, marketType)
                .map(this::toDomain);
    }

    @Override
    public List<TradingFeatureSnapshot> loadBetween(Instant startInclusive, Instant endExclusive) {
        return repository
                .findAllBySnapshotAtGreaterThanEqualAndSnapshotAtLessThanOrderBySnapshotAtAsc(
                        startInclusive, endExclusive)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private TradingFeatureSnapshot toDomain(TradingFeatureJpaEntity entity) {
        return new TradingFeatureSnapshot(
                entity.getFeatureId(),
                entity.getStockCode(),
                entity.getMarketType(),
                entity.getTechnicalFeatures(),
                entity.getNewsFeatures(),
                entity.getRiskFeatures(),
                entity.getSnapshotAt(),
                entity.getIdempotencyKey()
        );
    }
}
