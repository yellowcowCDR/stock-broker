package com.hermes.broker.market.adapter.out.external.interceptor;

import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.ratelimit.KisRateLimitCoordinator;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisRestClientInterceptor implements ClientHttpRequestInterceptor {

    private final KisRateLimitCoordinator rateLimitCoordinator;
    private final KisProperties kisProperties;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String rateLimitKey = kisProperties.environment().name() + ":" + kisProperties.account().rateLimitKey();

        // Order APIs do not retry. Query APIs do retry.
        boolean isQuery = request.getMethod() == HttpMethod.GET;

        if (!isQuery) {
            rateLimitCoordinator.acquire(rateLimitKey);
            return execution.execute(request, body);
        }

        KisProperties.RetryPolicy queryRetry = kisProperties.retry().query();
        int maxAttempts = Math.max(1, queryRetry.maxAttempts());
        Retry retry = createRetryConfig(queryRetry, maxAttempts);
        AtomicInteger attemptCounter = new AtomicInteger();

        try {
            return retry.executeCallable(() -> {
                int attempt = attemptCounter.incrementAndGet();
                rateLimitCoordinator.acquire(rateLimitKey);
                ClientHttpResponse response = execution.execute(request, body);
                HttpStatusCode statusCode;
                try {
                    statusCode = response.getStatusCode();
                } catch (IOException exception) {
                    response.close();
                    throw exception;
                }

                if (attempt < maxAttempts && isRetryableQueryStatus(statusCode)) {
                    log.warn(
                            "Retrying KIS query after HTTP {}: method={}, uri={}, attempt={}/{}",
                            statusCode.value(),
                            request.getMethod(),
                            request.getURI(),
                            attempt,
                            maxAttempts);
                    response.close();
                    throw new RetryableKisQueryException(statusCode);
                }
                return response;
            });
        } catch (Exception e) {
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
        }
    }

    private boolean isRetryableQueryStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }

    private Retry createRetryConfig(KisProperties.RetryPolicy queryRetry, int maxAttempts) {
        // Resilience4j IntervalFunction with Exponential Backoff and Jitter
        Duration initialDelay = queryRetry.initialDelay();
        double multiplier = queryRetry.multiplier();
        Duration maxDelay = queryRetry.maxDelay() != null
                ? queryRetry.maxDelay()
                : Duration.ofMillis(Long.MAX_VALUE);
        double jitter = 0.5; // Resilience4j uses a randomizationFactor (e.g. 0.5 means +/- 50%)

        IntervalFunction intervalFunction = IntervalFunction.ofExponentialRandomBackoff(
                initialDelay,
                multiplier,
                jitter,
                maxDelay
        );

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(intervalFunction)
                .retryExceptions(IOException.class)
                .build();

        return Retry.of("kisQueryRetry", config);
    }

    private static final class RetryableKisQueryException extends IOException {

        private RetryableKisQueryException(HttpStatusCode statusCode) {
            super("Retryable KIS query response: HTTP " + statusCode.value());
        }
    }
}
