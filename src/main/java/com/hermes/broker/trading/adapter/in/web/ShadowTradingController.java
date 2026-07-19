package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.trading.application.port.in.ManageShadowTradingUseCase;
import com.hermes.broker.trading.application.port.in.ShadowDecisionResult;
import com.hermes.broker.trading.application.port.in.StartShadowDecisionCommand;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/trading/shadow")
@RequiredArgsConstructor
public class ShadowTradingController {

    private final ManageShadowTradingUseCase manageShadowTradingUseCase;

    @PostMapping("/decisions")
    public ResponseEntity<ShadowDecisionResult> createShadowDecision(
            @Valid @RequestBody CreateTradingDecisionRequest request) {
        ShadowDecisionResult result = manageShadowTradingUseCase.start(
                new StartShadowDecisionCommand(
                        TradingHistoryController.toCommand(request), request.exchangeCode()));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @PostMapping("/samples/settle")
    public ResponseEntity<List<ShadowPerformanceSample>> settle(
            @RequestParam MarketType marketType,
            @RequestParam LocalDate tradingDate) {
        return ResponseEntity.ok(manageShadowTradingUseCase.settle(marketType, tradingDate));
    }

    @GetMapping("/samples")
    public ResponseEntity<List<ShadowPerformanceSample>> getSamples(
            @RequestParam String strategyVersion,
            @RequestParam(required = false) ShadowSampleStatus status) {
        return ResponseEntity.ok(
                manageShadowTradingUseCase.getSamples(strategyVersion, status));
    }
}
