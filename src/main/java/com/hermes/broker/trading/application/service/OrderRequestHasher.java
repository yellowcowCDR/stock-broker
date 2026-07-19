package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OverseasExchange;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OrderRequestHasher {

    public String hash(OrderRequestDto request) {
        String canonical = String.join("|",
                request.getMarketType().name(),
                request.getStockCode().trim().toUpperCase(),
                exchangeValue(request),
                request.getOrderType().name(),
                request.getPrice().stripTrailingZeros().toPlainString(),
                Integer.toString(request.getQuantity()),
                value(request.getDecisionId()),
                value(request.getFeatureId()),
                value(request.getStrategyVersion())
        );
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private String exchangeValue(OrderRequestDto request) {
        return request.getMarketType() == MarketType.OVERSEAS
                ? OverseasExchange.from(request.getExchangeCode()).orderExchangeCode()
                : "";
    }
}
