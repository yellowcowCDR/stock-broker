package com.hermes.broker.trading.dto;

import com.hermes.broker.trading.domain.OrderStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderResponseDto {
    private boolean success;
    private Long brokerOrderId;
    private String orderId;
    private String message;
    private OrderStatus status;
    private boolean replayed;
}
