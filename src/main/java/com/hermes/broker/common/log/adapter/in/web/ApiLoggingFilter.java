package com.hermes.broker.common.log.adapter.in.web;

import com.hermes.broker.common.log.adapter.out.persistence.ApiCallLogJpaEntity;
import com.hermes.broker.common.log.adapter.out.persistence.ApiCallLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ApiLoggingFilter extends OncePerRequestFilter {

    private final ApiCallLogRepository apiCallLogRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        // 정적 리소스나 헬스 체크 등 로깅이 불필요한 URL 패턴 필터링 (필요시 추가)
        if (request.getRequestURI().startsWith("/actuator") || request.getRequestURI().startsWith("/swagger-ui")) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper cachingRequestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachingResponseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(cachingRequestWrapper, cachingResponseWrapper);
        } finally {
            long executionTimeMs = System.currentTimeMillis() - startTime;

            try {
                logApiCall(cachingRequestWrapper, cachingResponseWrapper, executionTimeMs);
            } catch (Exception e) {
                log.error("Failed to log API call", e);
            }

            // 응답 Body를 클라이언트에게 전송하기 위해 복사
            cachingResponseWrapper.copyBodyToResponse();
        }
    }

    private void logApiCall(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long executionTimeMs) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (request.getQueryString() != null) {
            uri += "?" + request.getQueryString();
        }

        String requestHeaders = getHeadersAsString(request);
        String requestBody = getBodyAsString(request.getContentAsByteArray());
        
        int responseStatus = response.getStatus();
        String responseHeaders = getHeadersAsString(response);
        String responseBody = getBodyAsString(response.getContentAsByteArray());

        // Body 크기가 너무 클 경우 잘라내기 (DB 용량 및 성능 고려)
        if (requestBody.length() > 20000) {
            requestBody = requestBody.substring(0, 20000) + "... [TRUNCATED]";
        }
        if (responseBody.length() > 20000) {
            responseBody = responseBody.substring(0, 20000) + "... [TRUNCATED]";
        }

        ApiCallLogJpaEntity logEntity = ApiCallLogJpaEntity.builder()
                .httpMethod(method)
                .uri(uri)
                .requestHeaders(requestHeaders)
                .requestBody(requestBody)
                .responseStatus(responseStatus)
                .responseHeaders(responseHeaders)
                .responseBody(responseBody)
                .executionTimeMs(executionTimeMs)
                .build();

        apiCallLogRepository.save(logEntity);
    }

    private String getHeadersAsString(HttpServletRequest request) {
        Map<String, String> headerMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headerMap.put(headerName, request.getHeader(headerName));
            }
        }
        return headerMap.toString();
    }

    private String getHeadersAsString(HttpServletResponse response) {
        Map<String, String> headerMap = new HashMap<>();
        for (String headerName : response.getHeaderNames()) {
            headerMap.put(headerName, response.getHeader(headerName));
        }
        return headerMap.toString();
    }

    private String getBodyAsString(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
