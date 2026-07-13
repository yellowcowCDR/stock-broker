package com.hermes.broker.common.exception;

public class OpenDartApiException extends RuntimeException {
    public OpenDartApiException(String message) {
        super(message);
    }
    public OpenDartApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
