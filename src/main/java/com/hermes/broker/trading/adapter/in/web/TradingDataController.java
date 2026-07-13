package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.trading.application.port.in.RunTradingCycleUseCase;
import com.hermes.broker.trading.domain.decision.TradingCycleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/broker/trading")
@RequiredArgsConstructor
public class TradingDataController {

    private final RunTradingCycleUseCase runTradingCycleUseCase;

    @PostMapping("/cycle/{stockCode}")
    public ResponseEntity<TradingCycleResult> runCycle(@PathVariable String stockCode) {
        return ResponseEntity.ok(runTradingCycleUseCase.runForStock(stockCode));
    }
}
