package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.out.LoadOverseasAccountDataPort;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverseasAccountDataServiceTest {

    private final LoadOverseasAccountDataPort port = mock(LoadOverseasAccountDataPort.class);
    private final OverseasAccountDataService service = new OverseasAccountDataService(port);

    @Test
    void normalizesSymbolAndReturnsCompleteCapacity() {
        OverseasOrderCapacity capacity = new OverseasOrderCapacity(
                "AAPL", "NASD", "USD", new BigDecimal("195.50"),
                new BigDecimal("2300"), new BigDecimal("3000000"),
                new BigDecimal("11"), new BigDecimal("11"), new BigDecimal("1380"),
                "KIS_OPEN_API:INQUIRE_PSAMOUNT:TTTS3007R", Instant.now(), true);
        when(port.loadOrderCapacity("AAPL", "NASD", new BigDecimal("195.50")))
                .thenReturn(capacity);

        OverseasOrderCapacity result = service.getOrderCapacity(
                " aapl ", "nasd", new BigDecimal("195.50"));

        assertThat(result).isEqualTo(capacity);
    }

    @Test
    void invalidExchangeIsRejectedBeforeKisCall() {
        assertThatThrownBy(() -> service.getOrderCapacity(
                "AAPL", "UNKNOWN", new BigDecimal("195.50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NASD, NAS, NYSE, AMEX");
    }
}
