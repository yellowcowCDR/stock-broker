package com.hermes.broker.summary.adapter.out.persistence;

import com.hermes.broker.summary.domain.DailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailySummaryJpaRepository extends JpaRepository<DailySummary, Long> {
    
    // 특정 일자의 회고 데이터가 이미 존재하는지 확인
    Optional<DailySummary> findByTradeDate(LocalDate tradeDate);
}
