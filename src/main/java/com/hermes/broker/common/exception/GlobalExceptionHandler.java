package com.hermes.broker.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import com.hermes.broker.common.monitoring.OperationalEventRecorder;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OperationalEventRecorder operationalEventRecorder;

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

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> handleRequestBindingException(
            ServletRequestBindingException ex,
            HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ActiveAgentSkillNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(Exception ex, HttpServletRequest request) {
        log.warn("Not found error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DataPipelineUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDataPipelineUnavailable(
            DataPipelineUnavailableException ex,
            HttpServletRequest request) {
        log.error("Required real-data pipeline is unavailable: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(CronExecutionConflictException.class)
    public ResponseEntity<ErrorResponse> handleCronExecutionConflict(
            CronExecutionConflictException ex,
            HttpServletRequest request) {
        log.warn("Cron execution conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
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

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseException(
            DataAccessException ex, HttpServletRequest request) {
        operationalEventRecorder.recordDatabaseFailure(request.getMethod() + " "
                + request.getRequestURI(), ex);
        log.error("Broker database operation failed", ex);
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Broker database operation failed.",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("Method not supported: {} {}", ex.getMethod(), request.getRequestURI());
        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getHeaders());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Malformed request body for {} {}", request.getMethod(), request.getRequestURI());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Malformed request body.",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("Unsupported media type for {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getContentType());
        return buildErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type. Set Content-Type to application/json.",
                request.getRequestURI(),
                ex.getHeaders());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        log.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception occurred", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error occurred", request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, String path) {
        return buildErrorResponse(status, message, path, HttpHeaders.EMPTY);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            String path,
            HttpHeaders headers) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(clock.instant())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).headers(headers).body(response);
    }
}
