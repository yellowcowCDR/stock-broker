package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.trading.application.port.in.GetPortfolioSummaryUseCase;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/broker/trading/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final GetPortfolioSummaryUseCase getPortfolioSummaryUseCase;

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummary> getPortfolioSummary() {
        return ResponseEntity.ok(getPortfolioSummaryUseCase.getPortfolioSummary());
    }
}
