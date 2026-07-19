package com.hermes.broker.market.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.application.port.out.SaveMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MarketContextPersistenceAdapter implements SaveMarketContextPort, LoadMarketContextPort {

    private final MarketContextJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public MarketContext save(MarketContext context) {
        MarketContextJpaEntity entity = new MarketContextJpaEntity();
        entity.setContextId(context.contextId());
        entity.setMarketType(context.marketType());
        entity.setEntryPolicy(context.entryPolicy());
        entity.setRiskMultiplier(context.riskMultiplier());
        entity.setOverviewSnapshot(objectMapper.convertValue(
                context.overviewSnapshot(), new TypeReference<Map<String, Object>>() {}));
        entity.setRationale(context.rationale());
        entity.setOverviewDataSource(context.overviewSnapshot().dataSource());
        entity.setOverviewFetchedAt(context.overviewSnapshot().fetchedAt());
        entity.setAnalyzedBy(context.analyzedBy());
        entity.setCorrelationId(context.correlationId());
        entity.setAnalyzedAt(context.analyzedAt());
        entity.setValidUntil(context.validUntil());
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<MarketContext> loadById(String contextId) {
        return repository.findById(contextId).map(this::toDomain);
    }

    @Override
    public Optional<MarketContext> loadLatest(MarketType marketType) {
        return repository.findFirstByMarketTypeOrderByAnalyzedAtDesc(marketType).map(this::toDomain);
    }

    @Override
    public List<MarketContext> loadHistory(MarketType marketType, int limit) {
        return repository.findAllByMarketTypeOrderByAnalyzedAtDesc(
                        marketType, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private MarketContext toDomain(MarketContextJpaEntity entity) {
        MarketOverview overview = objectMapper.convertValue(
                entity.getOverviewSnapshot(), MarketOverview.class);
        return new MarketContext(
                entity.getContextId(),
                entity.getMarketType(),
                entity.getEntryPolicy(),
                entity.getRiskMultiplier(),
                overview,
                entity.getRationale(),
                entity.getAnalyzedBy(),
                entity.getCorrelationId(),
                entity.getAnalyzedAt(),
                entity.getValidUntil()
        );
    }
}
