package com.hermes.broker.market.dto;

import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.trading.domain.MarketType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateMarketContextRequest(
        @NotNull MarketType marketType,
        @NotNull MarketEntryPolicy entryPolicy,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal riskMultiplier,
        Instant validUntil,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 500) String> rationale,
        @NotBlank @Size(max = 100) String analyzedBy,
        @Size(max = 100) String correlationId
) {
}
