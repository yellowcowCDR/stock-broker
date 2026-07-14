package com.hermes.broker.market.adapter.out.external.ratelimit;

public interface KisRateLimitCoordinator {
    void acquire(String rateLimitKey);
}
