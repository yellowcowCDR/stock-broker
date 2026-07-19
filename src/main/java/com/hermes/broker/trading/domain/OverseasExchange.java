package com.hermes.broker.trading.domain;

import java.util.Locale;

/**
 * KIS overseas order exchange codes and their matching overseas quote exchange codes.
 */
public enum OverseasExchange {
    NASD("NAS"),
    NYSE("NYS"),
    AMEX("AMS");

    private final String quoteExchangeCode;

    OverseasExchange(String quoteExchangeCode) {
        this.quoteExchangeCode = quoteExchangeCode;
    }

    public String orderExchangeCode() {
        return name();
    }

    public String quoteExchangeCode() {
        return quoteExchangeCode;
    }

    public static OverseasExchange from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("exchangeCode is required for OVERSEAS orders.");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NAS", "NASD", "NASDAQ" -> NASD;
            case "NYS", "NYSE" -> NYSE;
            case "AMS", "AMEX", "NYSE_AMERICAN" -> AMEX;
            default -> throw new IllegalArgumentException(
                    "Unsupported US exchangeCode: " + value + ". Supported values are NASD, NYSE and AMEX.");
        };
    }
}
