package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradingLogRepository {
    TradingLog save(TradingLog tradingLog);
    List<TradingLog> findAllByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime start, LocalDateTime end);
    Optional<TradingLog> findById(Long id);
    List<TradingLog> findByStatus(OrderStatus status);
}
