package com.hermes.broker.market.adapter.out.external.ratelimit;

import com.hermes.broker.common.property.KisProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class Resilience4jKisRateLimitCoordinator implements KisRateLimitCoordinator {

    private final KisProperties kisProperties;
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    @Override
    public void acquire(String rateLimitKey) {
        if (!kisProperties.rateLimit().enabled()) {
            return;
        }

        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(rateLimitKey, this::createRateLimiter);
        try {
            RateLimiter.waitForPermission(rateLimiter);
        } catch (Exception e) {
            throw new RuntimeException("Failed to acquire rate limit permit for " + rateLimitKey, e);
        }
    }

    private RateLimiter createRateLimiter(String key) {
        int rps = kisProperties.rateLimit().requestsPerSecond();
        Duration timeoutDuration = kisProperties.rateLimit().acquireTimeout() != null 
            ? kisProperties.rateLimit().acquireTimeout() 
            : Duration.ofSeconds(30);
            
        // For MOCK, the interval is 1100ms. RateLimiter config requires period duration and limit.
        // E.g. limitForPeriod = 1, limitRefreshPeriod = 1100ms.
        // For PRODUCTION NEW_APPLICANT, requestsPerSecond = 3, minimumInterval = 400ms.
        // If minimumInterval > 1000/rps, we should use minimumInterval as the refresh period for 1 permit.
        // For example, 3 req/sec could mean 3 permits per 1000ms. 
        // But the requirement says "minimum interval 400ms", meaning strictly 1 request per 400ms.
        
        Duration interval = kisProperties.rateLimit().minimumInterval();
        if (interval == null) {
            // fallback if interval is not set but rps is
            interval = Duration.ofMillis((long) Math.ceil(1000.0 / rps));
        }

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(interval)
                .limitForPeriod(1) // 1 request per interval guarantees the minimum interval
                .timeoutDuration(timeoutDuration)
                .build();

        log.info("Created RateLimiter for key: {}, limitRefreshPeriod: {}, limitForPeriod: {}", key, interval, 1);
        return RateLimiter.of(key, config);
    }
}
