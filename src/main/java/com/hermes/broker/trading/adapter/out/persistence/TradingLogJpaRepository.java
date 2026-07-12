package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradingLogJpaRepository extends JpaRepository<TradingLog, Long> {

    // 특정 일자의 거래 내역을 조회하기 위한 쿼리 메서드 (장 마감 회고용)
    List<TradingLog> findAllByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime startOfDay, LocalDateTime endOfDay);
    List<TradingLog> findByStatus(OrderStatus status);
}
