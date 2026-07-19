package com.hermes.broker.summary.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import com.hermes.broker.trading.domain.MarketType;

@Repository
public interface TradingReflectionJpaRepository extends JpaRepository<TradingReflectionJpaEntity, String> {
    Optional<TradingReflectionJpaEntity> findByTradingDateAndMarketTypeAndStrategyVersion(
            LocalDate tradingDate, MarketType marketType, String strategyVersion);
    List<TradingReflectionJpaEntity> findAllByTradingDateOrderByCreatedAtAsc(LocalDate tradingDate);
    List<TradingReflectionJpaEntity> findAllByStrategyVersionAndDataCompleteTrueOrderByTradingDateAsc(
            String strategyVersion);
}
