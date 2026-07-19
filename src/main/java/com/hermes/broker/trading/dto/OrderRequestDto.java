package com.hermes.broker.trading.dto;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import lombok.Builder;
import lombok.Getter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
public class OrderRequestDto {
    @NotNull
    private MarketType marketType;

    @NotBlank
    @Size(max = 20)
    private String stockCode;

    /** Required for OVERSEAS orders. KIS order values: NASD, NYSE, AMEX. */
    @Size(max = 10)
    private String exchangeCode;

    @NotNull
    private OrderType orderType;

    @NotNull
    @DecimalMin(value = "0.0001")
    private BigDecimal price;

    @Min(1)
    private int quantity;

    @NotBlank
    @Size(max = 160)
    private String idempotencyKey;

    @Size(max = 36)
    private String decisionId;

    @Size(max = 36)
    private String featureId;

    @Size(max = 50)
    private String strategyVersion;

    private String decisionReason;
    private Map<String, Object> snapshotIndicators;
}
