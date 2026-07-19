package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.summary.application.port.in.GetTradingReflectionUseCase;
import com.hermes.broker.summary.domain.TradingReflection;
import com.hermes.broker.trading.application.port.in.GetTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.GetTradingFeatureUseCase;
import com.hermes.broker.trading.application.port.in.CreateTradingFeatureUseCase;
import com.hermes.broker.trading.application.port.in.CreateTradingFeatureCommand;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionCommand;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/trading")
@RequiredArgsConstructor
public class TradingHistoryController {

    private final GetTradingFeatureUseCase getTradingFeatureUseCase;
    private final GetTradingDecisionUseCase getTradingDecisionUseCase;
    private final GetTradingReflectionUseCase getTradingReflectionUseCase;
    private final CreateTradingFeatureUseCase createTradingFeatureUseCase;
    private final CreateTradingDecisionUseCase createTradingDecisionUseCase;

    @PostMapping("/features")
    public ResponseEntity<TradingFeatureSnapshot> createFeature(
            @Valid @RequestBody CreateTradingFeatureRequest request) {
        TradingFeatureSnapshot feature = createTradingFeatureUseCase.createFeature(
                new CreateTradingFeatureCommand(
                        request.stockCode(), request.marketType(), request.technicalFeatures(),
                        request.newsFeatures(), request.riskFeatures(), request.idempotencyKey()));
        return ResponseEntity.status(HttpStatus.CREATED).body(feature);
    }

    @PostMapping("/decisions")
    public ResponseEntity<TradingDecision> createActiveDecision(
            @Valid @RequestBody CreateTradingDecisionRequest request) {
        TradingDecision decision = createTradingDecisionUseCase.createActiveDecision(
                toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(decision);
    }

    @GetMapping("/features/latest")
    public ResponseEntity<TradingFeatureSnapshot> getLatestFeature(
            @RequestParam String stockCode,
            @RequestParam MarketType marketType) {
        return getTradingFeatureUseCase.getLatestFeature(stockCode, marketType)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/features")
    public ResponseEntity<List<TradingFeatureSnapshot>> getFeaturesByDate(
            @RequestParam String date) {
        return ResponseEntity.ok(getTradingFeatureUseCase.getFeaturesByDate(date));
    }

    @GetMapping("/decisions")
    public ResponseEntity<List<TradingDecision>> getDecisionsByDate(
            @RequestParam String date) {
        return ResponseEntity.ok(getTradingDecisionUseCase.getDecisionsByDate(date));
    }

    @GetMapping("/reflections")
    public ResponseEntity<List<TradingReflection>> getReflectionsByDate(
            @RequestParam String date) {
        return ResponseEntity.ok(getTradingReflectionUseCase.getReflectionsByDate(date));
    }

    static CreateTradingDecisionCommand toCommand(CreateTradingDecisionRequest request) {
        return new CreateTradingDecisionCommand(
                request.featureId(), request.decisionType(), request.strategyVersion(),
                request.reason(), request.recommendedPrice(), request.recommendedQuantity(),
                request.idempotencyKey());
    }
}
