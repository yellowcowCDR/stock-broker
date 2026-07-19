package com.hermes.broker.trading.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverseasExchangeTest {

    @Test
    void normalizesKisOrderAndQuoteExchangeCodes() {
        assertThat(OverseasExchange.from("NASDAQ")).isEqualTo(OverseasExchange.NASD);
        assertThat(OverseasExchange.from("NYS")).isEqualTo(OverseasExchange.NYSE);
        assertThat(OverseasExchange.from("AMEX").quoteExchangeCode()).isEqualTo("AMS");
    }

    @Test
    void rejectsUnknownExchange() {
        assertThatThrownBy(() -> OverseasExchange.from("US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NASD, NYSE and AMEX");
    }
}
