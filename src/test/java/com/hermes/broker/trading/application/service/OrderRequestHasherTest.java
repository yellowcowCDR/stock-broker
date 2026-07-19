package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.dto.OrderRequestDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestHasherTest {

    private final OrderRequestHasher hasher = new OrderRequestHasher();

    @Test
    void decisionAuditLinkageIsPartOfIdempotencyPayload() {
        OrderRequestDto first = request("decision-1", "feature-1", "strategy-v1");
        OrderRequestDto anotherDecision = request("decision-2", "feature-1", "strategy-v1");

        assertThat(hasher.hash(first)).isNotEqualTo(hasher.hash(anotherDecision));
    }

    @Test
    void overseasExchangeIsPartOfIdempotencyPayload() {
        OrderRequestDto nasdaq = overseasRequest("NASD");
        OrderRequestDto nyse = overseasRequest("NYSE");

        assertThat(hasher.hash(nasdaq)).isNotEqualTo(hasher.hash(nyse));
        assertThat(hasher.hash(nasdaq)).isEqualTo(hasher.hash(overseasRequest("NASDAQ")));
    }

    private OrderRequestDto request(String decisionId, String featureId, String strategyVersion) {
        return OrderRequestDto.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .orderType(OrderType.BUY)
                .price(new BigDecimal("70000"))
                .quantity(1)
                .idempotencyKey("same-key")
                .decisionId(decisionId)
                .featureId(featureId)
                .strategyVersion(strategyVersion)
                .build();
    }

    private OrderRequestDto overseasRequest(String exchangeCode) {
        return OrderRequestDto.builder()
                .marketType(MarketType.OVERSEAS)
                .stockCode("TEST")
                .exchangeCode(exchangeCode)
                .orderType(OrderType.BUY)
                .price(new BigDecimal("10"))
                .quantity(1)
                .idempotencyKey("same-key")
                .build();
    }
}
