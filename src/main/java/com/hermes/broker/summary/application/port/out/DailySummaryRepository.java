package com.hermes.broker.summary.application.port.out;

import com.hermes.broker.summary.domain.DailySummary;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySummaryRepository {
    DailySummary save(DailySummary dailySummary);
    Optional<DailySummary> findByTradeDate(LocalDate tradeDate);
}
