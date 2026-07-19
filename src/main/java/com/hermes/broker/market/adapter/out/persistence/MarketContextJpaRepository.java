package com.hermes.broker.market.adapter.out.persistence;

import com.hermes.broker.trading.domain.MarketType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketContextJpaRepository extends JpaRepository<MarketContextJpaEntity, String> {
    Optional<MarketContextJpaEntity> findFirstByMarketTypeOrderByAnalyzedAtDesc(MarketType marketType);
    List<MarketContextJpaEntity> findAllByMarketTypeOrderByAnalyzedAtDesc(
            MarketType marketType, Pageable pageable);
}
