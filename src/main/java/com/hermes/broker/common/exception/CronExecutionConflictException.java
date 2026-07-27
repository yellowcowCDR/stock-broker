package com.hermes.broker.common.exception;

public class CronExecutionConflictException extends RuntimeException {

    public CronExecutionConflictException(String message) {
        super(message);
    }
}
