package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.port.out.SaveTradingFeaturePort;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TradingFeaturePersistenceAdapter implements SaveTradingFeaturePort {

    private final TradingFeatureJpaRepository repository;

    @Override
    public void save(TradingFeatureSnapshot snapshot) {
        TradingFeatureJpaEntity entity = new TradingFeatureJpaEntity();
        if (snapshot.featureId() != null) {
            entity.setFeatureId(snapshot.featureId());
        }
        entity.setStockCode(snapshot.stockCode());
        entity.setTechnicalFeatures(snapshot.technicalFeatures());
        entity.setNewsFeatures(snapshot.newsFeatures());
        entity.setRiskFeatures(snapshot.riskFeatures());
        entity.setSnapshotAt(snapshot.snapshotAt());
        
        repository.save(entity);
    }
}
