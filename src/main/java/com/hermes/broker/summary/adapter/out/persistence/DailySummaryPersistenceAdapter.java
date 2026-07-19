package com.hermes.broker.summary.adapter.out.persistence;

import com.hermes.broker.summary.application.port.out.DailySummaryRepository;
import com.hermes.broker.summary.domain.DailySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailySummaryPersistenceAdapter implements DailySummaryRepository {

    private final DailySummaryJpaRepository jpaRepository;

    @Override
    public DailySummary save(DailySummary dailySummary) {
        return jpaRepository.save(dailySummary);
    }

    @Override
    public java.util.Optional<DailySummary> findByMarketTypeAndTradeDate(
            com.hermes.broker.trading.domain.MarketType marketType, java.time.LocalDate tradeDate) {
        return jpaRepository.findByMarketTypeAndTradeDate(marketType, tradeDate);
    }

    @Override
    public java.util.Optional<DailySummary> findLatestBefore(
            com.hermes.broker.trading.domain.MarketType marketType, java.time.LocalDate tradeDate) {
        return jpaRepository.findFirstByMarketTypeAndTradeDateBeforeOrderByTradeDateDesc(marketType, tradeDate);
    }
}
