package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.market.application.port.in.GetMarketOverviewUseCase;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.trading.domain.MarketType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Market API", description = "시장 전체 지표 API")
@RestController
@RequestMapping("/api/v1/broker/market")
@RequiredArgsConstructor
public class MarketOverviewController {

    private final GetMarketOverviewUseCase getMarketOverviewUseCase;

    @Operation(summary = "시장 전체 breadth 및 투자자 수급 조회")
    @GetMapping("/overview")
    public ResponseEntity<MarketOverview> getOverview(
            @RequestParam(defaultValue = "DOMESTIC") MarketType marketType) {
        return ResponseEntity.ok(getMarketOverviewUseCase.getOverview(marketType));
    }
}
