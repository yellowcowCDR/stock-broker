package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;

@Component
@RequiredArgsConstructor
public class TradingLogPersistenceAdapter implements TradingLogRepository {

    private final TradingLogJpaRepository jpaRepository;

    @Override
    public TradingLog save(TradingLog tradingLog) {
        return jpaRepository.save(tradingLog);
    }

    @Override
    public List<TradingLog> findAllByCreatedAtRange(Instant startInclusive, Instant endExclusive) {
        return jpaRepository.findAllByCreatedAtRange(startInclusive, endExclusive);
    }

    @Override
    public Optional<TradingLog> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<TradingLog> findByStatus(OrderStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public Optional<TradingLog> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<TradingLog> findByExternalOrderId(String externalOrderId) {
        return jpaRepository.findFirstByExternalOrderIdOrderByCreatedAtDesc(externalOrderId);
    }

    @Override
    public Optional<TradingLog> findByDecisionId(String decisionId) {
        return jpaRepository.findByDecisionId(decisionId);
    }

    @Override
    public boolean existsOpenOrder(String accountKey, MarketType marketType, String stockCode, OrderType orderType) {
        return jpaRepository.existsByAccountKeyAndMarketTypeAndStockCodeAndOrderTypeAndStatusIn(
                accountKey,
                marketType,
                stockCode,
                orderType,
                List.of(OrderStatus.SUBMITTING, OrderStatus.SUBMITTED, OrderStatus.PENDING,
                        OrderStatus.PARTIALLY_EXECUTED, OrderStatus.CANCEL_REQUESTED, OrderStatus.UNKNOWN)
        );
    }

    @Override
    public long countSubmittedOrders(
            String accountKey, MarketType marketType, Instant startInclusive, Instant endExclusive) {
        return jpaRepository.countSubmittedOrders(
                accountKey,
                marketType,
                startInclusive,
                endExclusive,
                List.of(OrderStatus.SUBMITTING, OrderStatus.SUBMITTED, OrderStatus.PENDING,
                        OrderStatus.PARTIALLY_EXECUTED, OrderStatus.EXECUTED,
                        OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELED,
                        OrderStatus.PARTIALLY_EXECUTED_CANCELED, OrderStatus.UNKNOWN)
        );
    }
}
