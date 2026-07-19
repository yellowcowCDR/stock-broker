package com.hermes.broker.market.domain;

import java.time.Instant;
import java.time.LocalDate;

public record UsEarningsSchedule(
        String symbol,
        String name,
        LocalDate reportDate,
        LocalDate fiscalDateEnding,
        String estimate,
        String currency,
        Instant announcementTimeUtc,
        String announcementTimePrecision,
        String dataSource
) {
}
