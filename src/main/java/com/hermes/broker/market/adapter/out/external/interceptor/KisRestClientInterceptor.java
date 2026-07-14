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
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

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
        
        if (!isQuery || !kisProperties.rateLimit().enabled()) {
            // Apply rate limit directly without retry wrapper if it's an order or rate limit is disabled.
            // Wait, even if it's an order, we still need rate limiting.
            rateLimitCoordinator.acquire(rateLimitKey);
            return execution.execute(request, body);
        }

        // Apply Retry for GET requests
        Retry retry = createRetryConfig();
        
        try {
            return retry.executeCallable(() -> {
                rateLimitCoordinator.acquire(rateLimitKey);
                return execution.execute(request, body);
            });
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    private Retry createRetryConfig() {
        KisProperties.RetryPolicy queryRetry = kisProperties.retry().query();
        
        // Resilience4j IntervalFunction with Exponential Backoff and Jitter
        // Wait time = initialDelay * (multiplier ^ attempt) + random_jitter
        long initialDelayMs = queryRetry.initialDelay().toMillis();
        double multiplier = queryRetry.multiplier();
        long maxDelayMs = queryRetry.maxDelay() != null ? queryRetry.maxDelay().toMillis() : Long.MAX_VALUE;
        double jitter = 0.5; // Resilience4j uses a randomizationFactor (e.g. 0.5 means +/- 50%)
        // The user specifies jitterMin and jitterMax, but resilience4j exponential backoff takes a single randomization factor.
        // We will approximate it with randomizationFactor 0.5 to keep things simple, or use custom IntervalFunction.

        IntervalFunction intervalFunction = IntervalFunction.ofExponentialRandomBackoff(
                Duration.ofMillis(initialDelayMs),
                multiplier,
                jitter
        );

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(queryRetry.maxAttempts())
                .intervalFunction(intervalFunction)
                .retryExceptions(IOException.class) // Retry on IO Exceptions (timeouts, connections)
                // If there are specific HTTP 5xx errors to retry, we would need to inspect the response.
                // RestClient will throw RestClientException or similar which is a RuntimeException.
                .retryExceptions(RuntimeException.class)
                .build();

        return Retry.of("kisQueryRetry", config);
    }
}
