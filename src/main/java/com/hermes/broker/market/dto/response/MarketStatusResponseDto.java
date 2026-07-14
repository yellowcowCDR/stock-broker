package com.hermes.broker.market.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class MarketStatusResponseDto {
    private String marketType;
    private boolean isOpen;
    private String status; // e.g., "OPEN", "CLOSED", "HOLIDAY"
    private ZonedDateTime checkedAt;
}
