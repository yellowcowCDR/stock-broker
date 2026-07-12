package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;

import java.util.List;

public interface AgentTradingUseCase {
    OrderResponseDto placeOrder(OrderRequestDto request);
    List<TradingLog> getDailyLogs();
}
