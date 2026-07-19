package com.hermes.broker.common.monitoring;

import java.time.Instant;

public record SourceObservation(
        String source,
        Instant lastSuccessAt,
        Instant dataFetchedAt,
        boolean complete,
        String freshness,
        Instant lastFailureAt,
        String lastFailureMessage
) {
}
