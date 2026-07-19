package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.market.application.port.in.CreateMarketContextCommand;
import com.hermes.broker.market.application.port.in.MarketContextUseCase;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.dto.CreateMarketContextRequest;
import com.hermes.broker.trading.domain.MarketType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/trading/market-contexts")
@RequiredArgsConstructor
public class MarketContextController {

    private final MarketContextUseCase marketContextUseCase;

    @PostMapping
    public ResponseEntity<MarketContext> create(@Valid @RequestBody CreateMarketContextRequest request) {
        MarketContext created = marketContextUseCase.create(new CreateMarketContextCommand(
                request.marketType(),
                request.entryPolicy(),
                request.riskMultiplier(),
                request.validUntil(),
                request.rationale(),
                request.analyzedBy(),
                request.correlationId()
        ));
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/latest")
    public ResponseEntity<MarketContext> latest(
            @RequestParam(defaultValue = "DOMESTIC") MarketType marketType) {
        return ResponseEntity.of(marketContextUseCase.getLatest(marketType));
    }

    @GetMapping
    public ResponseEntity<List<MarketContext>> history(
            @RequestParam(defaultValue = "DOMESTIC") MarketType marketType) {
        return ResponseEntity.ok(marketContextUseCase.getHistory(marketType));
    }
}
