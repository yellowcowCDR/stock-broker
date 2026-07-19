package com.hermes.broker.common.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data.watchlist")
public record MarketWatchlistProperties(
        int domesticLimit,
        int overseasPerExchangeLimit,
        int maxCandidates,
        long minDomesticVolume
) {
}
