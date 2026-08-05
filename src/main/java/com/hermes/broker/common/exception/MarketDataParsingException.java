package com.hermes.broker.common.exception;

public class MarketDataParsingException extends RuntimeException {
    public MarketDataParsingException(String message) {
        super(message);
    }

    public MarketDataParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
