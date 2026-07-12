package com.hermes.broker.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class,
            InvalidStockCodeException.class,
            InvalidAgentSkillParametersException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("Bad request error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(ActiveAgentSkillNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(Exception ex, HttpServletRequest request) {
        log.warn("Not found error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleRestClientException(RestClientResponseException ex, HttpServletRequest request) {
        String responseBody = ex.getResponseBodyAsString();
        String errorMessage = "External API error occurred";
        log.error("External API Error: Status={}, Body={}", ex.getStatusCode(), responseBody, ex);

        try {
            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                String msgCd = jsonNode.path("msg_cd").asText("");
                String msg1 = jsonNode.path("msg1").asText("");
                
                if (!msgCd.isBlank() || !msg1.isBlank()) {
                    errorMessage = String.format("[KIS Error: %s] %s", msgCd, msg1);
                } else {
                    errorMessage = String.format("[External API Error: %s]", ex.getStatusCode());
                }
            }
        } catch (Exception parseException) {
            log.warn("Failed to parse error response body: {}", responseBody);
            errorMessage = String.format("[External API Error: %s] %s", ex.getStatusCode(), ex.getStatusText());
        }

        return buildErrorResponse(HttpStatus.BAD_GATEWAY, errorMessage, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception occurred", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error occurred", request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, String path) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
