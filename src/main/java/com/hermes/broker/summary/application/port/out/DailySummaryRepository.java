package com.hermes.broker.summary.application.port.out;

import com.hermes.broker.summary.domain.DailySummary;

import java.time.LocalDate;
import java.util.Optional;
import com.hermes.broker.trading.domain.MarketType;

public interface DailySummaryRepository {
    DailySummary save(DailySummary dailySummary);
    Optional<DailySummary> findByMarketTypeAndTradeDate(MarketType marketType, LocalDate tradeDate);
    Optional<DailySummary> findLatestBefore(MarketType marketType, LocalDate tradeDate);
}
