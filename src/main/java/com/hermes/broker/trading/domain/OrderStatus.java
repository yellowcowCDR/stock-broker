package com.hermes.broker.trading.domain;

public enum OrderStatus {
    VALIDATING,
    REJECTED,
    SUBMITTING,
    PENDING,
    SUBMITTED,
    PARTIALLY_EXECUTED,
    EXECUTED,
    CANCEL_REQUESTED,
    CANCELED,
    PARTIALLY_EXECUTED_CANCELED,
    UNKNOWN,
    FAILED
}
