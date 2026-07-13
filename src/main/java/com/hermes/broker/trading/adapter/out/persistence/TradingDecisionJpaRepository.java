package com.hermes.broker.trading.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradingDecisionJpaRepository extends JpaRepository<TradingDecisionJpaEntity, String> {
}
