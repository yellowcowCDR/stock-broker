package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.market.application.port.in.MarketFundamentalUseCase;
import com.hermes.broker.market.application.port.in.MarketIntelligenceUseCase;
import com.hermes.broker.market.application.port.in.MarketNewsUseCase;
import com.hermes.broker.market.dto.response.FundamentalsResponseDto;
import com.hermes.broker.market.dto.response.IntelligenceResponseDto;
import com.hermes.broker.market.dto.response.NewsResponseDto;
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

    @Operation(summary = "Get Fundamentals", description = "Fetches corporate profile, recent disclosures, and financial statements via OpenDART")
    @GetMapping("/fundamentals")
    public ResponseEntity<FundamentalsResponseDto> getFundamentals(
            @Parameter(description = "Stock code (e.g. 005930)", required = true)
            @RequestParam("stockCode") String stockCode) {
        return ResponseEntity.ok(fundamentalUseCase.getFundamentals(stockCode));
    }

    @Operation(summary = "Get News", description = "Fetches and cleans recent news articles via Naver News API")
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
}
