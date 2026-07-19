package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.port.out.LoadShadowPerformanceSamplePort;
import com.hermes.broker.trading.application.port.out.SaveShadowPerformanceSamplePort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ShadowPerformanceSamplePersistenceAdapter
        implements LoadShadowPerformanceSamplePort, SaveShadowPerformanceSamplePort {

    private final ShadowPerformanceSampleJpaRepository repository;

    @Override
    public ShadowPerformanceSample save(ShadowPerformanceSample sample) {
        ShadowPerformanceSampleJpaEntity entity = new ShadowPerformanceSampleJpaEntity();
        entity.setSampleId(sample.sampleId());
        entity.setDecisionId(sample.decisionId());
        entity.setFeatureId(sample.featureId());
        entity.setStrategyVersion(sample.strategyVersion());
        entity.setStockCode(sample.stockCode());
        entity.setMarketType(sample.marketType());
        entity.setExchangeCode(sample.exchangeCode());
        entity.setDecisionType(sample.decisionType());
        entity.setReferencePrice(sample.referencePrice());
        entity.setObservedPrice(sample.observedPrice());
        entity.setRawReturnRate(sample.rawReturnRate());
        entity.setActionReturnRate(sample.actionReturnRate());
        entity.setTradingDate(sample.tradingDate());
        entity.setStatus(sample.status());
        entity.setDataSource(sample.dataSource());
        entity.setStartedAt(sample.startedAt());
        entity.setObservedAt(sample.observedAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<ShadowPerformanceSample> loadByDecisionId(String decisionId) {
        return repository.findByDecisionId(decisionId).map(this::toDomain);
    }

    @Override
    public List<ShadowPerformanceSample> loadByStrategyVersion(
            String strategyVersion, ShadowSampleStatus status) {
        return repository.findAllByStrategyVersionAndStatusOrderByStartedAtAsc(
                        strategyVersion, status).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ShadowPerformanceSample> loadByMarketAndTradingDateAndStatus(
            MarketType marketType, LocalDate tradingDate, ShadowSampleStatus status) {
        return repository.findAllByMarketTypeAndTradingDateAndStatusOrderByStartedAtAsc(
                        marketType, tradingDate, status).stream()
                .map(this::toDomain)
                .toList();
    }

    private ShadowPerformanceSample toDomain(ShadowPerformanceSampleJpaEntity entity) {
        return new ShadowPerformanceSample(
                entity.getSampleId(), entity.getDecisionId(), entity.getFeatureId(),
                entity.getStrategyVersion(), entity.getStockCode(), entity.getMarketType(),
                entity.getExchangeCode(), entity.getDecisionType(), entity.getReferencePrice(),
                entity.getObservedPrice(), entity.getRawReturnRate(), entity.getActionReturnRate(),
                entity.getTradingDate(), entity.getStatus(), entity.getDataSource(),
                entity.getStartedAt(), entity.getObservedAt());
    }
}
