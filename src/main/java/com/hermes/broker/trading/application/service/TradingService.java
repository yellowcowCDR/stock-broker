package com.hermes.broker.trading.application.service;

import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import com.hermes.broker.trading.application.port.in.AgentTradingUseCase;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService implements AgentTradingUseCase {

    private final TradingLogRepository tradingLogRepository;
    private final MarketTradingPort marketTradingPort;
    private final MarketTimeValidator timeValidator;

    @Override
    @Transactional
    public OrderResponseDto placeOrder(OrderRequestDto request) {
        timeValidator.validateMarketOpen();

        TradingLog logEntry = TradingLog.builder()
                .marketType(request.getMarketType() != null ? request.getMarketType() : MarketType.DOMESTIC)
                .stockCode(request.getStockCode())
                .stockName("UNKNOWN") 
                .orderType(request.getOrderType())
                .orderPrice(request.getPrice())
                .orderQuantity(request.getQuantity())
                .status(OrderStatus.PENDING)
                .decisionReason(request.getDecisionReason())
                .snapshotIndicators(request.getSnapshotIndicators())
                .build();
                
        tradingLogRepository.save(logEntry);
        
        OrderResponseDto response = marketTradingPort.placeOrder(request);
        
        if (!response.isSuccess()) {
            logEntry.updateStatus(OrderStatus.FAILED);
            tradingLogRepository.save(logEntry);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradingLog> getDailyLogs() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        
        return tradingLogRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(startOfDay, endOfDay);
    }
}
