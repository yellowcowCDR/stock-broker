package com.hermes.broker.summary.adapter.in.web;

import com.hermes.broker.summary.application.port.in.RunDailyReflectionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.hermes.broker.summary.domain.TradingReflection;
import com.hermes.broker.trading.domain.MarketType;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/trading/reflections")
@RequiredArgsConstructor
public class ReflectionController {

    private final RunDailyReflectionUseCase runDailyReflectionUseCase;

    @PostMapping("/run")
    public ResponseEntity<List<TradingReflection>> runDailyReflection(
            @RequestParam(defaultValue = "DOMESTIC") MarketType marketType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradingDate) {
        return ResponseEntity.ok(runDailyReflectionUseCase.runDailyReflection(marketType, tradingDate));
    }
}
