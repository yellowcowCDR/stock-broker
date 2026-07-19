package com.hermes.broker.trading.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingLogSettlementTest {

    @Test
    void preservesCombinedKisCostAndCalculatesAdverseBuySlippage() {
        TradingLog order = order(OrderType.BUY);
        order.updateExecution(new BigDecimal("70100"), new BigDecimal("2"));

        order.reconcileExecutionCost(
                new BigDecimal("315.50"), "KRW",
                "KIS_INQUIRE_DAILY_CCLD:prsm_tlex_smtl:ESTIMATED_COMBINED",
                Instant.parse("2026-07-19T07:00:00Z"));

        assertThat(order.isCostDataComplete()).isTrue();
        assertThat(order.getTransactionCost()).isEqualByComparingTo("315.50");
        assertThat(order.getSlippageAmount()).isEqualByComparingTo("200");
        assertThat(order.getCostSource()).contains("ESTIMATED_COMBINED");
    }

    @Test
    void rejectsNegativeProviderCostInsteadOfNormalizingIt() {
        TradingLog order = order(OrderType.SELL);
        order.updateExecution(new BigDecimal("69900"), BigDecimal.ONE);

        assertThatThrownBy(() -> order.reconcileExecutionCost(
                new BigDecimal("-1"), "KRW", "KIS", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.isCostDataComplete()).isFalse();
    }

    private TradingLog order(OrderType orderType) {
        return TradingLog.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .stockName("Samsung")
                .orderType(orderType)
                .orderPrice(new BigDecimal("70000"))
                .orderQuantity(2)
                .status(OrderStatus.EXECUTED)
                .build();
    }
}
