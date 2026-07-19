package com.hermes.broker.summary.adapter.out.persistence;

import com.hermes.broker.summary.application.port.out.SaveTradingReflectionPort;
import com.hermes.broker.summary.application.port.out.LoadTradingReflectionPort;
import com.hermes.broker.summary.domain.TradingReflection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TradingReflectionPersistenceAdapter implements SaveTradingReflectionPort, LoadTradingReflectionPort {

    private final TradingReflectionJpaRepository repository;

    @Override
    public void save(TradingReflection reflection) {
        TradingReflectionJpaEntity entity = new TradingReflectionJpaEntity();
        if (reflection.reflectionId() != null) {
            entity.setReflectionId(reflection.reflectionId());
        }
        entity.setTradingDate(reflection.tradingDate());
        entity.setMarketType(reflection.marketType());
        entity.setMarketZoneId(reflection.marketZoneId());
        entity.setStrategyVersion(reflection.strategyVersion());
        entity.setDailyReturnRate(reflection.dailyReturnRate());
        entity.setMarketReturnRate(reflection.marketReturnRate());
        entity.setTotalTransactionCost(reflection.totalTransactionCost());
        entity.setTotalSlippageAmount(reflection.totalSlippageAmount());
        entity.setDecisionCount(reflection.decisionCount());
        entity.setHoldCount(reflection.holdCount());
        entity.setBlockCount(reflection.blockCount());
        entity.setDataComplete(reflection.dataComplete());
        entity.setReviews(reflection.reviews());
        entity.setOverallFeedback(reflection.overallFeedback());
        entity.setImprovementPlan(reflection.improvementPlan());
        entity.setCreatedAt(reflection.createdAt());

        repository.save(entity);
    }

    @Override
    public List<TradingReflection> loadByTradingDate(LocalDate tradingDate) {
        return repository.findAllByTradingDateOrderByCreatedAtAsc(tradingDate)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public java.util.Optional<TradingReflection> loadByIdentity(
            LocalDate tradingDate, com.hermes.broker.trading.domain.MarketType marketType,
            String strategyVersion) {
        return repository.findByTradingDateAndMarketTypeAndStrategyVersion(
                        tradingDate, marketType, strategyVersion)
                .map(this::toDomain);
    }

    @Override
    public List<TradingReflection> loadCompleteByStrategyVersion(String strategyVersion) {
        return repository.findAllByStrategyVersionAndDataCompleteTrueOrderByTradingDateAsc(strategyVersion)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private TradingReflection toDomain(TradingReflectionJpaEntity entity) {
        return new TradingReflection(
                entity.getReflectionId(),
                entity.getTradingDate(),
                entity.getMarketType(),
                entity.getMarketZoneId(),
                entity.getStrategyVersion(),
                entity.getDailyReturnRate(),
                entity.getMarketReturnRate(),
                entity.getTotalTransactionCost(),
                entity.getTotalSlippageAmount(),
                entity.getDecisionCount(),
                entity.getHoldCount(),
                entity.getBlockCount(),
                entity.isDataComplete(),
                entity.getReviews(),
                entity.getOverallFeedback(),
                entity.getImprovementPlan(),
                entity.getCreatedAt()
        );
    }
}
