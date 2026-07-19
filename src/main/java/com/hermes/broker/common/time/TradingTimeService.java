package com.hermes.broker.common.time;

import com.hermes.broker.trading.domain.MarketType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Component
public class TradingTimeService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final Clock clock;

    public TradingTimeService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate currentMarketDate(MarketType marketType) {
        return LocalDate.now(clock.withZone(zoneFor(marketType)));
    }

    public TimeRange currentMarketDay(MarketType marketType) {
        return day(currentMarketDate(marketType), zoneFor(marketType));
    }

    public TimeRange currentUtcDay() {
        return day(LocalDate.now(clock.withZone(ZoneOffset.UTC)), ZoneOffset.UTC);
    }

    public TimeRange day(LocalDate date, ZoneId zoneId) {
        return new TimeRange(
                date.atStartOfDay(zoneId).toInstant(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant()
        );
    }

    public ZoneId zoneFor(MarketType marketType) {
        return marketType == MarketType.OVERSEAS ? NEW_YORK : SEOUL;
    }
}
