package com.hermes.broker.market.application.service;

import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.KisDomesticHolidayCalendar;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void mockEnvironmentUsesProductionHolidayCalendarBeforeAllowingDomesticOrders() {
        KisProperties kisProperties = mock(KisProperties.class);
        KisDomesticHolidayCalendar domesticCalendar = mock(KisDomesticHolidayCalendar.class);
        when(domesticCalendar.sessionFor(any())).thenReturn(
                new KisDomesticHolidayCalendar.DomesticHolidaySession(true, true, "KIS_OPEN_DAY")
        );
        MarketTimeValidator validator = new MarketTimeValidator(
                kisProperties,
                Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC),
                new UsEquityMarketCalendar(),
                domesticCalendar
        );

        var status = validator.getMarketStatus("DOMESTIC");

        assertTrue(status.isOpen());
        assertTrue(status.isComplete());
        assertEquals("REGULAR_MARKET", status.getStatus());
        verify(domesticCalendar).sessionFor(LocalDate.of(2026, 7, 28));
    }

    @Test
    void unavailableProductionHolidayLookupBlocksDomesticOrdersFailClosed() {
        KisDomesticHolidayCalendar domesticCalendar = mock(KisDomesticHolidayCalendar.class);
        when(domesticCalendar.sessionFor(any())).thenReturn(
                KisDomesticHolidayCalendar.DomesticHolidaySession.unavailable(
                        "DOMESTIC_HOLIDAY_CALENDAR_LOOKUP_FAILED")
        );
        MarketTimeValidator validator = new MarketTimeValidator(
                mock(KisProperties.class),
                Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC),
                new UsEquityMarketCalendar(),
                domesticCalendar
        );

        var status = validator.getMarketStatus("DOMESTIC");

        assertFalse(status.isOpen());
        assertFalse(status.isComplete());
        assertEquals("CALENDAR_UNAVAILABLE", status.getStatus());
        assertEquals("DOMESTIC_HOLIDAY_CALENDAR_LOOKUP_FAILED", status.getReason());
    }

    private MarketTimeValidator validatorAt(String instant) {
        return new MarketTimeValidator(
                mock(KisProperties.class),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                new UsEquityMarketCalendar(),
                mock(KisDomesticHolidayCalendar.class)
        );
    }
}
