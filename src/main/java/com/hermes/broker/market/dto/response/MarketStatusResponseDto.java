package com.hermes.broker.market.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class MarketStatusResponseDto {
    private String marketType;
    private boolean isOpen;
    private String status;
    private String reason;
    private String marketTimeZone;
    private LocalDate marketDate;
    private boolean earlyClose;
    private boolean complete;
    private String calendarSource;
    private Instant sessionOpensAt;
    private Instant sessionClosesAt;
    private Instant checkedAt;
}
