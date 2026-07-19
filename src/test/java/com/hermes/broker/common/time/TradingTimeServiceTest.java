package com.hermes.broker.common.time;

import com.hermes.broker.trading.domain.MarketType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingTimeServiceTest {

    @Test
    void newYorkTradingDayUsesDstAwareMidnightBoundaries() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-08T16:00:00Z"), ZoneOffset.UTC);
        TradingTimeService service = new TradingTimeService(clock);

        TimeRange range = service.currentMarketDay(MarketType.OVERSEAS);

        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), range.startInclusive());
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), range.endExclusive());
        assertEquals(Duration.ofHours(23), Duration.between(range.startInclusive(), range.endExclusive()));
    }
}
