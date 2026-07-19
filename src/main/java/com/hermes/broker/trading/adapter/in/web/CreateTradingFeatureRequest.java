package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.trading.domain.MarketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateTradingFeatureRequest(
        @NotBlank @Size(max = 20) String stockCode,
        @NotNull MarketType marketType,
        Map<String, Object> technicalFeatures,
        Map<String, Object> newsFeatures,
        @NotNull Map<String, Object> riskFeatures,
        @NotBlank @Size(max = 160) String idempotencyKey
) {
}
