package com.hermes.broker.trading.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderResponseDto {
    private boolean success;
    private String orderId;
    private String message;
}
