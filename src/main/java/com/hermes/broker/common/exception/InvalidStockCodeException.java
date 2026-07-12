package com.hermes.broker.common.exception;

public class InvalidStockCodeException extends RuntimeException {
    public InvalidStockCodeException(String message) {
        super(message);
    }
}
