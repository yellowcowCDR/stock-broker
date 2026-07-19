package com.hermes.broker.market.application.service;

import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.KisHeaderProvider;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MarketTimeValidatorTest {

    @Test
    void earlyCloseAllowsOrdersBeforeOnePmEastern() {
        var status = validatorAt("2026-11-27T17:59:59Z").getMarketStatus("OVERSEAS");

        assertTrue(status.isOpen());
        assertTrue(status.isEarlyClose());
        assertEquals("REGULAR_MARKET_EARLY_CLOSE", status.getStatus());
        assertEquals(Instant.parse("2026-11-27T18:00:00Z"), status.getSessionClosesAt());
    }

    @Test
    void earlyCloseBlocksOrdersAtOnePmEastern() {
        MarketTimeValidator validator = validatorAt("2026-11-27T18:00:00Z");
        var status = validator.getMarketStatus("OVERSEAS");

        assertFalse(status.isOpen());
        assertEquals("CLOSED_EARLY", status.getStatus());
        assertThrows(IllegalStateException.class, () -> validator.validateMarketOpen("OVERSEAS"));
    }

    @Test
    void holidayBlocksOrdersBeforeKisSubmission() {
        MarketTimeValidator validator = validatorAt("2026-11-26T15:00:00Z");
        var status = validator.getMarketStatus("OVERSEAS");

        assertFalse(status.isOpen());
        assertEquals("CLOSED_HOLIDAY", status.getStatus());
        assertEquals("THANKSGIVING_DAY", status.getReason());
        assertThrows(IllegalStateException.class, () -> validator.validateMarketOpen("OVERSEAS"));
    }

    private MarketTimeValidator validatorAt(String instant) {
        return new MarketTimeValidator(
                mock(RestClient.Builder.class),
                mock(KisHeaderProvider.class),
                mock(KisProperties.class),
                mock(KisRestClientInterceptor.class),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                new UsEquityMarketCalendar()
        );
    }
}
