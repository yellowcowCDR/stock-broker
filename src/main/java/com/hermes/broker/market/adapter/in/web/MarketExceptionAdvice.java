package com.hermes.broker.market.adapter.in.web;

import com.hermes.broker.common.exception.ExternalApiNotConfiguredException;
import com.hermes.broker.common.exception.NaverNewsApiException;
import com.hermes.broker.common.exception.OpenDartApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

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
}
