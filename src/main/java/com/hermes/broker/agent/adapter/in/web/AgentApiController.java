package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import com.hermes.broker.market.application.port.in.GetMarketPriceUseCase;
import com.hermes.broker.trading.application.port.in.AgentTradingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/broker/market")
@RequiredArgsConstructor
public class AgentApiController {

    private final GetMarketPriceUseCase getMarketPriceUseCase;
    private final AgentTradingUseCase agentTradingUseCase;

    @GetMapping("/price")
    public ResponseEntity<CurrentPriceDto> getPrice(
            @RequestParam String stockCode,
            @RequestParam MarketType marketType) {
        
        return ResponseEntity.ok(getMarketPriceUseCase.getPrice(stockCode, marketType));
    }

    @PostMapping("/order")
    public ResponseEntity<OrderResponseDto> placeOrder(@RequestBody OrderRequestDto request) {
        
        OrderResponseDto response = agentTradingUseCase.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/daily-logs")
    public ResponseEntity<List<TradingLog>> getDailyLogs() {
        return ResponseEntity.ok(agentTradingUseCase.getDailyLogs());
    }
}
