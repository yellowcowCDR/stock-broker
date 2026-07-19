package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;

public interface TradingLogRepository {
    TradingLog save(TradingLog tradingLog);
    List<TradingLog> findAllByCreatedAtRange(Instant startInclusive, Instant endExclusive);
    Optional<TradingLog> findById(Long id);
    List<TradingLog> findByStatus(OrderStatus status);
    Optional<TradingLog> findByIdempotencyKey(String idempotencyKey);
    Optional<TradingLog> findByExternalOrderId(String externalOrderId);
    Optional<TradingLog> findByDecisionId(String decisionId);
    boolean existsOpenOrder(String accountKey, MarketType marketType, String stockCode, OrderType orderType);
    long countSubmittedOrders(
            String accountKey, MarketType marketType, Instant startInclusive, Instant endExclusive);
}
