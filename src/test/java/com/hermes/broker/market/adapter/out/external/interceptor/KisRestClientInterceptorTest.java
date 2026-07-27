package com.hermes.broker.market.adapter.out.external.interceptor;

import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.property.KisProductionRateLimitType;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.ratelimit.KisRateLimitCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisRestClientInterceptorTest {

    @Test
    void retriesServerErrorsForGetRequestsAndReturnsSuccessfulResponse() throws Exception {
        KisRateLimitCoordinator coordinator = mock(KisRateLimitCoordinator.class);
        KisRestClientInterceptor interceptor =
                new KisRestClientInterceptor(coordinator, properties(3));
        HttpRequest request = request(HttpMethod.GET);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse firstFailure = response(HttpStatus.INTERNAL_SERVER_ERROR);
        ClientHttpResponse secondFailure = response(HttpStatus.INTERNAL_SERVER_ERROR);
        ClientHttpResponse success = response(HttpStatus.OK);
        when(execution.execute(request, new byte[0]))
                .thenReturn(firstFailure, secondFailure, success);

        ClientHttpResponse actual = interceptor.intercept(request, new byte[0], execution);

        assertThat(actual).isSameAs(success);
        verify(execution, times(3)).execute(request, new byte[0]);
        verify(coordinator, times(3)).acquire("PRODUCTION:primary");
        verify(firstFailure).close();
        verify(secondFailure).close();
        verify(success, never()).close();
    }

    @Test
    void returnsFinalServerErrorResponseSoRestClientCanPreserveItsBody() throws Exception {
        KisRateLimitCoordinator coordinator = mock(KisRateLimitCoordinator.class);
        KisRestClientInterceptor interceptor =
                new KisRestClientInterceptor(coordinator, properties(3));
        HttpRequest request = request(HttpMethod.GET);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse firstFailure = response(HttpStatus.INTERNAL_SERVER_ERROR);
        ClientHttpResponse secondFailure = response(HttpStatus.INTERNAL_SERVER_ERROR);
        ClientHttpResponse finalFailure = response(HttpStatus.INTERNAL_SERVER_ERROR);
        when(execution.execute(request, new byte[0]))
                .thenReturn(firstFailure, secondFailure, finalFailure);

        ClientHttpResponse actual = interceptor.intercept(request, new byte[0], execution);

        assertThat(actual).isSameAs(finalFailure);
        verify(execution, times(3)).execute(request, new byte[0]);
        verify(firstFailure).close();
        verify(secondFailure).close();
        verify(finalFailure, never()).close();
    }

    @Test
    void doesNotRetryClientErrorsOtherThanTooManyRequests() throws Exception {
        KisRateLimitCoordinator coordinator = mock(KisRateLimitCoordinator.class);
        KisRestClientInterceptor interceptor =
                new KisRestClientInterceptor(coordinator, properties(3));
        HttpRequest request = request(HttpMethod.GET);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse badRequest = response(HttpStatus.BAD_REQUEST);
        when(execution.execute(request, new byte[0])).thenReturn(badRequest);

        ClientHttpResponse actual = interceptor.intercept(request, new byte[0], execution);

        assertThat(actual).isSameAs(badRequest);
        verify(execution).execute(request, new byte[0]);
        verify(badRequest, never()).close();
    }

    @Test
    void neverRetriesOrderRequests() throws Exception {
        KisRateLimitCoordinator coordinator = mock(KisRateLimitCoordinator.class);
        KisRestClientInterceptor interceptor =
                new KisRestClientInterceptor(coordinator, properties(3));
        HttpRequest request = request(HttpMethod.POST);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse failure = response(HttpStatus.INTERNAL_SERVER_ERROR);
        when(execution.execute(request, new byte[0])).thenReturn(failure);

        ClientHttpResponse actual = interceptor.intercept(request, new byte[0], execution);

        assertThat(actual).isSameAs(failure);
        verify(execution).execute(request, new byte[0]);
        verify(coordinator).acquire("PRODUCTION:primary");
    }

    private HttpRequest request(HttpMethod method) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getURI()).thenReturn(URI.create("https://example.test/kis"));
        return request;
    }

    private ClientHttpResponse response(HttpStatus status) throws Exception {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getStatusCode()).thenReturn(status);
        return response;
    }

    private KisProperties properties(int maxAttempts) {
        KisProperties.RetryPolicy queryPolicy = new KisProperties.RetryPolicy(
                maxAttempts,
                Duration.ofMillis(1),
                1.0,
                Duration.ofMillis(1),
                Duration.ZERO,
                Duration.ZERO);
        KisProperties.RetryPolicy orderPolicy = new KisProperties.RetryPolicy(
                1,
                Duration.ofMillis(1),
                1.0,
                Duration.ofMillis(1),
                Duration.ZERO,
                Duration.ZERO);

        return new KisProperties(
                KisEnvironment.PRODUCTION,
                "https://example.test",
                KisProductionRateLimitType.NEW_APPLICANT,
                null,
                new KisProperties.Account("primary"),
                new KisProperties.RateLimit(true, 3, Duration.ofMillis(400), Duration.ofSeconds(1)),
                new KisProperties.Retry(queryPolicy, orderPolicy),
                null,
                null);
    }
}
