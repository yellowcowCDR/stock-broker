package com.hermes.broker.trading.dto;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
public class OrderRequestDto {
    private MarketType marketType;
    private String stockCode;
    private OrderType orderType;
    private BigDecimal price;
    private int quantity;
    private String decisionReason;
    private Map<String, Object> snapshotIndicators;
}
