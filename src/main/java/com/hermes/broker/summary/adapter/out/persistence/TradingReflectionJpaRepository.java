package com.hermes.broker.summary.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TradingReflectionJpaRepository extends JpaRepository<TradingReflectionJpaEntity, String> {
    Optional<TradingReflectionJpaEntity> findByTradingDateAndStrategyVersion(LocalDate tradingDate, String strategyVersion);
}
