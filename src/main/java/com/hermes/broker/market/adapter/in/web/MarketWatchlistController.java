package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.market.application.port.in.GetMarketWatchlistUseCase;
import com.hermes.broker.market.domain.MarketWatchlistResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Market API", description = "종목 및 시세 관련 API")
@RestController
@RequestMapping("/api/v1/broker/market")
@RequiredArgsConstructor
public class MarketWatchlistController {

    private final GetMarketWatchlistUseCase getMarketWatchlistUseCase;

    @Operation(
            summary = "실시장 관심 종목 후보 조회",
            description = "KIS 거래대금 순위 기반 분석 후보군입니다. 매수 신호가 아니며 실데이터가 불완전하면 실패합니다."
    )
    @GetMapping("/watchlist")
    public ResponseEntity<MarketWatchlistResult> getWatchlist() {
        return ResponseEntity.ok(getMarketWatchlistUseCase.getWatchlist());
    }
}
