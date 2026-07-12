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
    public java.util.Optional<DailySummary> findByTradeDate(java.time.LocalDate tradeDate) {
        return jpaRepository.findByTradeDate(tradeDate);
    }
}
