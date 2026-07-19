package com.hermes.broker.common.log.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalManagementAuditFilter extends OncePerRequestFilter {

    public static final String ACTOR_HEADER = "X-Actor";
    public static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean mutating = switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
        String uri = request.getRequestURI();
        boolean auditedInternalMutation = uri.startsWith("/api/v1/internal/agent")
                || uri.startsWith("/api/v1/internal/trading");
        return !mutating || !auditedInternalMutation;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String actor = request.getHeader(ACTOR_HEADER);
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (!valid(actor, 100) || !valid(correlationId, 160)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"X-Actor and X-Correlation-ID headers are required for internal trading mutations.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean valid(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
    }
}
