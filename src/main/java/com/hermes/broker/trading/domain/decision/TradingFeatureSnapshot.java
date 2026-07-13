package com.hermes.broker.trading.domain.decision;

import java.time.LocalDateTime;
import java.util.Map;

public record TradingFeatureSnapshot(
        String featureId,
        String stockCode,
        Map<String, Object> technicalFeatures,
        Map<String, Object> newsFeatures,
        Map<String, Object> riskFeatures,
        LocalDateTime snapshotAt
) {
}
