package com.hermes.broker.common.exception;

public class NaverNewsApiException extends RuntimeException {
    public NaverNewsApiException(String message) {
        super(message);
    }
    public NaverNewsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
