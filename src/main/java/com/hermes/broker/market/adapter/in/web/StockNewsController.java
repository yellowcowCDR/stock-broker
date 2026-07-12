package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.market.application.port.in.GetStockNewsUseCase;
import com.hermes.broker.market.domain.StockNews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Market API", description = "종목 및 시세 관련 API")
@RestController
@RequestMapping("/api/v1/broker/market")
@RequiredArgsConstructor
public class StockNewsController {

    private final GetStockNewsUseCase getStockNewsUseCase;

    @Operation(summary = "종목별 뉴스 감성 분석 조회", description = "특정 종목의 최신 뉴스 및 감성 정보를 조회합니다.")
    @GetMapping("/news")
    public ResponseEntity<Map<String, Object>> getNews(@RequestParam String stockCode) {
        List<StockNews> news = getStockNewsUseCase.getNews(stockCode);
        return ResponseEntity.ok(Map.of(
                "stockCode", stockCode,
                "news", news
        ));
    }
}
