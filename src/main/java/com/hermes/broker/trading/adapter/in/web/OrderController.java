package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.trading.application.port.in.CancelOrderUseCase;
import com.hermes.broker.trading.application.port.in.GetOpenOrdersUseCase;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/broker/orders")
@RequiredArgsConstructor
public class OrderController {

    private final GetOpenOrdersUseCase getOpenOrdersUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    @GetMapping("/open")
    public ResponseEntity<List<OpenOrder>> getOpenOrders() {
        return ResponseEntity.ok(getOpenOrdersUseCase.getOpenOrders());
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable String orderId,
            @RequestParam String stockCode,
            @RequestParam MarketType marketType,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        cancelOrderUseCase.cancelOrder(orderId, stockCode, marketType, idempotencyKey);
        return ResponseEntity.ok().build();
    }
}
