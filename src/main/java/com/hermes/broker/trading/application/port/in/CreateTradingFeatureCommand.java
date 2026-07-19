package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.MarketType;

import java.util.Map;

public record CreateTradingFeatureCommand(
        String stockCode,
        MarketType marketType,
        Map<String, Object> technicalFeatures,
        Map<String, Object> newsFeatures,
        Map<String, Object> riskFeatures,
        String idempotencyKey
) {
}
