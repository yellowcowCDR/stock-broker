package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.market.application.port.in.MarketFundamentalUseCase;
import com.hermes.broker.market.application.port.in.MarketIntelligenceUseCase;
import com.hermes.broker.market.application.port.in.MarketNewsUseCase;
import com.hermes.broker.market.application.port.in.GetUsFundamentalsUseCase;
import com.hermes.broker.market.domain.UsFundamentalsSnapshot;
import com.hermes.broker.market.dto.response.FundamentalsResponseDto;
import com.hermes.broker.market.dto.response.IntelligenceResponseDto;
import com.hermes.broker.market.dto.response.NewsResponseDto;
import com.hermes.broker.market.dto.response.MarketStatusResponseDto;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/broker/market")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Endpoints for fetching market fundamentals and news")
public class MarketDataController {

    private final MarketFundamentalUseCase fundamentalUseCase;
    private final MarketNewsUseCase newsUseCase;
    private final MarketIntelligenceUseCase intelligenceUseCase;
    private final MarketTimeValidator marketTimeValidator;
    private final GetUsFundamentalsUseCase getUsFundamentalsUseCase;

    @Operation(summary = "Get Fundamentals", description = "Fetches corporate profile, recent disclosures, and financial statements via OpenDART")
    @GetMapping("/fundamentals")
    public ResponseEntity<FundamentalsResponseDto> getFundamentals(
            @Parameter(description = "Stock code (e.g. 005930)", required = true)
            @RequestParam("stockCode") String stockCode) {
        return ResponseEntity.ok(fundamentalUseCase.getFundamentals(stockCode));
    }

    @Operation(
            summary = "Get United States Fundamentals",
            description = "Alpha Vantage company overview, financial statements, earnings history and upcoming earnings calendar with completeness metadata"
    )
    @GetMapping("/us-fundamentals")
    public ResponseEntity<UsFundamentalsSnapshot> getUsFundamentals(
            @Parameter(description = "United States stock symbol (e.g. AAPL)", required = true)
            @RequestParam("stockCode") String stockCode) {
        return ResponseEntity.ok(getUsFundamentalsUseCase.getUsFundamentals(stockCode));
    }

    @Operation(
            summary = "Get News",
            description = "Fetches Naver News API articles and returns source, collection time, completeness, freshness and rule-based analysis metadata"
    )
    @GetMapping("/news")
    public ResponseEntity<NewsResponseDto> getNews(
            @Parameter(description = "Keyword or Stock code", required = true)
            @RequestParam("stockCode") String stockCode) {
        return ResponseEntity.ok(newsUseCase.getNews(stockCode));
    }

    @Operation(summary = "Get Intelligence", description = "Aggregates fundamentals and news data into a single intelligence report")
    @GetMapping("/intelligence")
    public ResponseEntity<IntelligenceResponseDto> getIntelligence(
            @Parameter(description = "Stock code (e.g. 005930)", required = true)
            @RequestParam("stockCode") String stockCode) {
        return ResponseEntity.ok(intelligenceUseCase.getIntelligence(stockCode));
    }

    @Operation(summary = "Get Market Status", description = "주식 시장 개장 여부 및 현재 상태 조회")
    @GetMapping("/status")
    public ResponseEntity<MarketStatusResponseDto> getMarketStatus(
            @Parameter(description = "DOMESTIC or OVERSEAS")
            @RequestParam(value = "marketType", defaultValue = "DOMESTIC") String marketType) {
        return ResponseEntity.ok(marketTimeValidator.getMarketStatus(marketType));
    }
}
