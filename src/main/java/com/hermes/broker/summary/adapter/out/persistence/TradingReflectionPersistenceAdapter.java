package com.hermes.broker.summary.adapter.out.persistence;

import com.hermes.broker.summary.application.port.out.SaveTradingReflectionPort;
import com.hermes.broker.summary.domain.TradingReflection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TradingReflectionPersistenceAdapter implements SaveTradingReflectionPort {

    private final TradingReflectionJpaRepository repository;

    @Override
    public void save(TradingReflection reflection) {
        TradingReflectionJpaEntity entity = new TradingReflectionJpaEntity();
        if (reflection.reflectionId() != null) {
            entity.setReflectionId(reflection.reflectionId());
        }
        entity.setTradingDate(reflection.tradingDate());
        entity.setStrategyVersion(reflection.strategyVersion());
        entity.setDailyReturnRate(reflection.dailyReturnRate());
        entity.setMarketReturnRate(reflection.marketReturnRate());
        entity.setReviews(reflection.reviews());
        entity.setOverallFeedback(reflection.overallFeedback());
        entity.setImprovementPlan(reflection.improvementPlan());
        entity.setCreatedAt(reflection.createdAt());

        repository.save(entity);
    }
}
