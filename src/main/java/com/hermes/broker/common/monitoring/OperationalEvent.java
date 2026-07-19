package com.hermes.broker.common.monitoring;

import java.time.Instant;

public record OperationalEvent(String type, Instant occurredAt, String message) {
}
