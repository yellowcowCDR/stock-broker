package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.port.out.SaveTradingDecisionPort;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TradingDecisionPersistenceAdapter implements SaveTradingDecisionPort {

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

        repository.save(entity);
    }
}
