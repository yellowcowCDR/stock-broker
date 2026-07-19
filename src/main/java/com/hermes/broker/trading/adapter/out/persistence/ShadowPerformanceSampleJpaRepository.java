package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShadowPerformanceSampleJpaRepository
        extends JpaRepository<ShadowPerformanceSampleJpaEntity, String> {
    Optional<ShadowPerformanceSampleJpaEntity> findByDecisionId(String decisionId);

    List<ShadowPerformanceSampleJpaEntity> findAllByStrategyVersionAndStatusOrderByStartedAtAsc(
            String strategyVersion, ShadowSampleStatus status);

    List<ShadowPerformanceSampleJpaEntity>
    findAllByMarketTypeAndTradingDateAndStatusOrderByStartedAtAsc(
            MarketType marketType, LocalDate tradingDate, ShadowSampleStatus status);
}
