package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;

public interface SubmitOrderPort {
    boolean supports(MarketType marketType);
    OrderResponseDto placeOrder(OrderRequestDto orderRequest);
}
