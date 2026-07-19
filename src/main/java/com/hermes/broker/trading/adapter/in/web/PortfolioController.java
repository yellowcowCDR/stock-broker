package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.trading.application.port.in.GetPortfolioSummaryUseCase;
import com.hermes.broker.trading.application.port.in.GetOverseasAccountDataUseCase;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/broker/account")
@RequiredArgsConstructor
public class PortfolioController {

    private final GetPortfolioSummaryUseCase getPortfolioSummaryUseCase;
    private final GetOverseasAccountDataUseCase getOverseasAccountDataUseCase;

    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioSummary> getPortfolioSummary() {
        return ResponseEntity.ok(getPortfolioSummaryUseCase.getPortfolioSummary());
    }

    @GetMapping("/overseas/us")
    public ResponseEntity<OverseasAccountSnapshot> getUnitedStatesAccount() {
        return ResponseEntity.ok(getOverseasAccountDataUseCase.getUnitedStatesAccount());
    }

    @GetMapping("/overseas/order-capacity")
    public ResponseEntity<OverseasOrderCapacity> getOverseasOrderCapacity(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "NASD") String exchangeCode,
            @RequestParam BigDecimal orderPrice) {
        return ResponseEntity.ok(getOverseasAccountDataUseCase.getOrderCapacity(
                stockCode, exchangeCode, orderPrice));
    }
}
