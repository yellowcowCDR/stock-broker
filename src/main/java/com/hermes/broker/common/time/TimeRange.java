package com.hermes.broker.common.time;

import java.time.Instant;

public record TimeRange(Instant startInclusive, Instant endExclusive) {
}
