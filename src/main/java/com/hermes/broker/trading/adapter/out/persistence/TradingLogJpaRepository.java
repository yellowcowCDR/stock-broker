package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.hermes.broker.trading.domain.MarketType;

@Repository
public interface TradingLogJpaRepository extends JpaRepository<TradingLog, Long> {

    // 특정 일자의 거래 내역을 조회하기 위한 쿼리 메서드 (장 마감 회고용)
    @Query("""
            SELECT t FROM TradingLog t
            WHERE t.createdAt >= :startInclusive AND t.createdAt < :endExclusive
            ORDER BY t.createdAt ASC
            """)
    List<TradingLog> findAllByCreatedAtRange(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);
    List<TradingLog> findByStatus(OrderStatus status);
    Optional<TradingLog> findByIdempotencyKey(String idempotencyKey);
    Optional<TradingLog> findFirstByExternalOrderIdOrderByCreatedAtDesc(String externalOrderId);
    Optional<TradingLog> findByDecisionId(String decisionId);
    List<TradingLog> findTop5ByMarketTypeAndCostDataCompleteFalseAndStatusInOrderByCreatedAtDesc(
            MarketType marketType, List<OrderStatus> statuses);
    boolean existsByAccountKeyAndMarketTypeAndStockCodeAndOrderTypeAndStatusIn(
            String accountKey,
            com.hermes.broker.trading.domain.MarketType marketType,
            String stockCode,
            com.hermes.broker.trading.domain.OrderType orderType,
            List<OrderStatus> statuses
    );
    @Query("""
            SELECT COUNT(t) FROM TradingLog t
            WHERE t.accountKey = :accountKey
              AND t.marketType = :marketType
              AND t.createdAt >= :startInclusive
              AND t.createdAt < :endExclusive
              AND t.status IN :statuses
            """)
    long countSubmittedOrders(
            @Param("accountKey") String accountKey,
            @Param("marketType") MarketType marketType,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("statuses") List<OrderStatus> statuses);
}
