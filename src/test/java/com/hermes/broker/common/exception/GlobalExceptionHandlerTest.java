package com.hermes.broker.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.broker.common.monitoring.OperationalEventRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
}
