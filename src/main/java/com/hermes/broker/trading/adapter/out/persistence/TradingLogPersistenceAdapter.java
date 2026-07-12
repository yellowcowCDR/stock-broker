package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TradingLogPersistenceAdapter implements TradingLogRepository {

    private final TradingLogJpaRepository jpaRepository;

    @Override
    public TradingLog save(TradingLog tradingLog) {
        return jpaRepository.save(tradingLog);
    }

    @Override
    public List<TradingLog> findAllByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(start, end);
    }

    @Override
    public Optional<TradingLog> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<TradingLog> findByStatus(OrderStatus status) {
        return jpaRepository.findByStatus(status);
    }
}
