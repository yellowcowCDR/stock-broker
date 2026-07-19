package com.hermes.broker.trading.domain.decision;

import com.hermes.broker.trading.domain.MarketType;

import java.time.Instant;
import java.util.Map;

public record TradingFeatureSnapshot(
        String featureId,
        String stockCode,
        MarketType marketType,
        Map<String, Object> technicalFeatures,
        Map<String, Object> newsFeatures,
        Map<String, Object> riskFeatures,
        Instant snapshotAt,
        String idempotencyKey
) {
    public TradingFeatureSnapshot(
            String featureId,
            String stockCode,
            MarketType marketType,
            Map<String, Object> technicalFeatures,
            Map<String, Object> newsFeatures,
            Map<String, Object> riskFeatures,
            Instant snapshotAt
    ) {
        this(featureId, stockCode, marketType, technicalFeatures, newsFeatures,
                riskFeatures, snapshotAt, null);
    }
}
