package com.hermes.broker.market.domain;

import java.time.LocalDate;

public record UsHistoricalEarnings(
        LocalDate fiscalDateEnding,
        LocalDate reportedDate,
        String reportedEps,
        String estimatedEps,
        String surprise,
        String surprisePercentage,
        String reportTime
) {
}
