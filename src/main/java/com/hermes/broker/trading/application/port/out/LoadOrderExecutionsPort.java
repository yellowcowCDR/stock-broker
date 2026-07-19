package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderExecutionSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface LoadOrderExecutionsPort {
    boolean supports(MarketType marketType);

    List<OrderExecutionSnapshot> loadOrderExecutions(LocalDate from, LocalDate to);
}
