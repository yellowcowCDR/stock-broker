package com.hermes.broker.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Default Config (10 minutes) for naver_news, opendart_disclosure, etc.
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000));
        
        // Custom TTL for 24h caches
        cacheManager.registerCustomCache("opendart_corp_code",
                Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(10).buildAsync().synchronous());
        
        cacheManager.registerCustomCache("opendart_profile",
                Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(1000).buildAsync().synchronous());

        cacheManager.registerCustomCache("kis_stock_sector",
                Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(5000).buildAsync().synchronous());

        cacheManager.registerCustomCache("kis_market_overview",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(10).buildAsync().synchronous());

        cacheManager.registerCustomCache("alpha_vantage_us_fundamentals",
                Caffeine.newBuilder().expireAfterWrite(12, TimeUnit.HOURS).maximumSize(1000).buildAsync().synchronous());

        return cacheManager;
    }
}
