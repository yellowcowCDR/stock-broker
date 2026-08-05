package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.common.exception.ExternalApiNotConfiguredException;
import com.hermes.broker.common.exception.NaverNewsApiException;
import com.hermes.broker.common.exception.MarketDataParsingException;
import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.common.exception.OpenDartApiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.hermes.broker.market.adapter.in.web")
public class MarketExceptionAdvice {

    @ExceptionHandler(ExternalApiNotConfiguredException.class)
    public ResponseEntity<Map<String, String>> handleExternalApiNotConfigured(ExternalApiNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "External API not configured", "message", ex.getMessage()));
    }

    @ExceptionHandler(OpenDartApiException.class)
    public ResponseEntity<Map<String, String>> handleOpenDartApi(OpenDartApiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "OpenDART API Error", "message", ex.getMessage()));
    }

    @ExceptionHandler(NaverNewsApiException.class)
    public ResponseEntity<Map<String, String>> handleNaverNewsApi(NaverNewsApiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Naver News API Error", "message", ex.getMessage()));
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleMarketDataUnavailable(MarketDataUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Real market data unavailable", "message", ex.getMessage()));
    }

    @ExceptionHandler(MarketDataParsingException.class)
    public ResponseEntity<Map<String, String>> handleMarketDataParsingException(MarketDataParsingException ex) {
        log.error("外部 API 데이터 파싱 실패. KIS 응답 명세가 변경되었는지 확인하세요.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server parsing error", "message", ex.getMessage()));
    }
}
