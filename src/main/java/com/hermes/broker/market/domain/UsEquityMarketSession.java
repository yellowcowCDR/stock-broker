package com.hermes.broker.market.domain;

import java.time.Instant;
import java.time.LocalDate;

public record UsEquityMarketSession(
        LocalDate marketDate,
        boolean tradingDay,
        boolean earlyClose,
        boolean complete,
        String reason,
        Instant opensAt,
        Instant closesAt
) {
}
