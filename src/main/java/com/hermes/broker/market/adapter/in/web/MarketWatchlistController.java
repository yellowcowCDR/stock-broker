package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.market.application.port.in.GetMarketWatchlistUseCase;
import com.hermes.broker.market.domain.WatchlistStock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Market API", description = "종목 및 시세 관련 API")
@RestController
@RequestMapping("/api/v1/broker/market")
@RequiredArgsConstructor
public class MarketWatchlistController {

    private final GetMarketWatchlistUseCase getMarketWatchlistUseCase;

    @Operation(summary = "관심 종목 조회", description = "당일 분석할 관심 종목 후보군을 조회합니다.")
    @GetMapping("/watchlist")
    public ResponseEntity<Map<String, List<WatchlistStock>>> getWatchlist() {
        List<WatchlistStock> stocks = getMarketWatchlistUseCase.getWatchlist();
        return ResponseEntity.ok(Map.of("stocks", stocks));
    }
}
