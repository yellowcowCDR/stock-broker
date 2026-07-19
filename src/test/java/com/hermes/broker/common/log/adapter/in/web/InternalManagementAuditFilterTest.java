package com.hermes.broker.common.log.adapter.in.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalManagementAuditFilterTest {

    private final InternalManagementAuditFilter filter = new InternalManagementAuditFilter();

    @Test
    void rejectsInternalAgentMutationWithoutAuditHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/internal/agent/skills/candidates");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("X-Correlation-ID");
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsMutationWithActorAndCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/internal/agent/skills/candidates");
        request.addHeader("X-Actor", "hermes-shadow-cron");
        request.addHeader("X-Correlation-ID", "candidate-20260719-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsInternalTradingMutationWithoutAuditHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/internal/trading/features");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("X-Correlation-ID");
        verifyNoInteractions(chain);
    }
}
