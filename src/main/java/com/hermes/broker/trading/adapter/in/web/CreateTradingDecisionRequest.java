package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTradingDecisionRequest(
        @NotBlank @Size(max = 36) String featureId,
        @NotNull TradingDecisionType decisionType,
        @Min(1) int strategyVersion,
        @NotBlank @Size(max = 1000) String reason,
        @DecimalMin(value = "0.0001") BigDecimal recommendedPrice,
        @DecimalMin(value = "0.0001") BigDecimal recommendedQuantity,
        @NotBlank @Size(max = 160) String idempotencyKey,
        @Size(max = 10) String exchangeCode
) {
}
