package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OverseasAccountSnapshot(
        String countryCode,
        String currency,
        BigDecimal cashBalance,
        BigDecimal availableForUse,
        List<OverseasPosition> positions,
        String dataSource,
        Instant fetchedAt,
        boolean complete
) {
    public OverseasAccountSnapshot {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
}
