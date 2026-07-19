package com.hermes.broker.market.application.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsEquityMarketCalendarTest {

    private final UsEquityMarketCalendar calendar = new UsEquityMarketCalendar();

    @Test
    void newYorkDstChangesUtcSessionTimeAutomatically() {
        var beforeDst = calendar.sessionFor(LocalDate.of(2026, 3, 6));
        var afterDst = calendar.sessionFor(LocalDate.of(2026, 3, 9));

        assertEquals(Instant.parse("2026-03-06T14:30:00Z"), beforeDst.opensAt());
        assertEquals(Instant.parse("2026-03-09T13:30:00Z"), afterDst.opensAt());
        assertEquals(Instant.parse("2026-03-06T21:00:00Z"), beforeDst.closesAt());
        assertEquals(Instant.parse("2026-03-09T20:00:00Z"), afterDst.closesAt());
    }

    @Test
    void publishedHolidayIsClosed() {
        var session = calendar.sessionFor(LocalDate.of(2026, 4, 3));

        assertFalse(session.tradingDay());
        assertTrue(session.complete());
        assertEquals("GOOD_FRIDAY", session.reason());
    }

    @Test
    void publishedEarlyCloseEndsAtOnePmEastern() {
        var session = calendar.sessionFor(LocalDate.of(2026, 11, 27));

        assertTrue(session.tradingDay());
        assertTrue(session.earlyClose());
        assertEquals(Instant.parse("2026-11-27T18:00:00Z"), session.closesAt());
    }

    @Test
    void unpublishedCalendarYearIsFailClosed() {
        var session = calendar.sessionFor(LocalDate.of(2029, 1, 2));

        assertFalse(session.tradingDay());
        assertFalse(session.complete());
        assertEquals("CALENDAR_YEAR_NOT_PUBLISHED", session.reason());
    }
}
