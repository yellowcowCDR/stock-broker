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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Tag(name = "Agent Market API", description = "자동 매매 에이전트 전용 시세 및 주문 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/broker/market")
@RequiredArgsConstructor
public class AgentApiController {

    private final GetMarketPriceUseCase getMarketPriceUseCase;
    private final AgentTradingUseCase agentTradingUseCase;

    @Operation(summary = "현재가 조회", description = "종목 코드와 시장 타입(국내/해외)을 입력받아 현재 주식 가격을 조회합니다.")
    @GetMapping("/price")
    public ResponseEntity<CurrentPriceDto> getPrice(
            @RequestParam String stockCode,
            @RequestParam MarketType marketType) {
        
        return ResponseEntity.ok(getMarketPriceUseCase.getPrice(stockCode, marketType));
    }

    @Operation(summary = "주식 주문(매수/매도)", description = "에이전트의 판단에 따라 주식 매수 또는 매도 주문을 전송합니다.")
    @PostMapping("/order")
    public ResponseEntity<OrderResponseDto> placeOrder(@RequestBody OrderRequestDto request) {
        
        OrderResponseDto response = agentTradingUseCase.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "당일 매매 일지 조회", description = "당일 발생한 모든 주문 및 체결 내역을 조회합니다.")
    @GetMapping("/daily-logs")
    public ResponseEntity<List<TradingLog>> getDailyLogs() {
        return ResponseEntity.ok(agentTradingUseCase.getDailyLogs());
    }
}
