package com.hermes.broker.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.broker.common.monitoring.OperationalEventRecorder;
import com.hermes.broker.common.monitoring.CronHeartbeatService;
import com.hermes.broker.common.monitoring.adapter.in.web.CronHeartbeatController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void mapsMissingResourceToNotFoundInsteadOfInternalServerError() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new ObjectMapper(), clock, mock(OperationalEventRecorder.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/unknown");
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET, "api/v1/unknown");

        var response = handler.handleNoResourceFound(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/unknown");
    }

    @Test
    void resolvesMissingResourceToSpecificHandlerBeforeCatchAll() {
        ExceptionHandlerMethodResolver resolver =
                new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

        var method = resolver.resolveMethod(
                new NoResourceFoundException(HttpMethod.GET, "missing"));

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("handleNoResourceFound");
    }

    @Test
    void mapsCronExecutionConflictToConflictInsteadOfBadRequest() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new ObjectMapper(), clock, mock(OperationalEventRecorder.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/internal/operations/cron-heartbeats");

        var response = handler.handleCronExecutionConflict(
                new CronExecutionConflictException(
                        "Another execution is already STARTED for Cron krx-paper-cycle1."),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
    }

    @Test
    void rejectsGetOnPostOnlyHeartbeatEndpointWithMethodNotAllowed() throws Exception {
        MockMvc mockMvc = heartbeatMockMvc();

        mockMvc.perform(get("/api/v1/internal/operations/cron-heartbeats"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void rejectsMalformedJsonWithBadRequest() throws Exception {
        MockMvc mockMvc = heartbeatMockMvc();

        mockMvc.perform(post("/api/v1/internal/operations/cron-heartbeats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body."));
    }

    @Test
    void rejectsMissingContentTypeWithUnsupportedMediaType() throws Exception {
        MockMvc mockMvc = heartbeatMockMvc();

        mockMvc.perform(post("/api/v1/internal/operations/cron-heartbeats")
                        .content("""
                                {
                                  "cronName": "test",
                                  "executionId": "run-1",
                                  "phase": "STARTED",
                                  "expectedIntervalSeconds": 300
                                }
                                """))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message")
                        .value("Unsupported media type. Set Content-Type to application/json."));
    }

    private MockMvc heartbeatMockMvc() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new ObjectMapper(), clock, mock(OperationalEventRecorder.class));
        CronHeartbeatController controller =
                new CronHeartbeatController(mock(CronHeartbeatService.class));

        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler)
                .build();
    }
}
