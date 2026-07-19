package com.hermes.broker.market.application.service;

import com.hermes.broker.market.domain.UsEquityMarketSession;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

@Component
public class UsEquityMarketCalendar {

    public static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    public static final String CALENDAR_SOURCE =
            "https://www.nyse.com/trade/hours-calendars#2026-2028";

    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime EARLY_CLOSE = LocalTime.of(13, 0);
    private static final Set<Integer> SUPPORTED_YEARS = Set.of(2026, 2027, 2028);

    private static final Map<LocalDate, String> HOLIDAYS = Map.ofEntries(
            Map.entry(LocalDate.of(2026, 1, 1), "NEW_YEARS_DAY"),
            Map.entry(LocalDate.of(2026, 1, 19), "MARTIN_LUTHER_KING_JR_DAY"),
            Map.entry(LocalDate.of(2026, 2, 16), "WASHINGTONS_BIRTHDAY"),
            Map.entry(LocalDate.of(2026, 4, 3), "GOOD_FRIDAY"),
            Map.entry(LocalDate.of(2026, 5, 25), "MEMORIAL_DAY"),
            Map.entry(LocalDate.of(2026, 6, 19), "JUNETEENTH"),
            Map.entry(LocalDate.of(2026, 7, 3), "INDEPENDENCE_DAY_OBSERVED"),
            Map.entry(LocalDate.of(2026, 9, 7), "LABOR_DAY"),
            Map.entry(LocalDate.of(2026, 11, 26), "THANKSGIVING_DAY"),
            Map.entry(LocalDate.of(2026, 12, 25), "CHRISTMAS_DAY"),

            Map.entry(LocalDate.of(2027, 1, 1), "NEW_YEARS_DAY"),
            Map.entry(LocalDate.of(2027, 1, 18), "MARTIN_LUTHER_KING_JR_DAY"),
            Map.entry(LocalDate.of(2027, 2, 15), "WASHINGTONS_BIRTHDAY"),
            Map.entry(LocalDate.of(2027, 3, 26), "GOOD_FRIDAY"),
            Map.entry(LocalDate.of(2027, 5, 31), "MEMORIAL_DAY"),
            Map.entry(LocalDate.of(2027, 6, 18), "JUNETEENTH_OBSERVED"),
            Map.entry(LocalDate.of(2027, 7, 5), "INDEPENDENCE_DAY_OBSERVED"),
            Map.entry(LocalDate.of(2027, 9, 6), "LABOR_DAY"),
            Map.entry(LocalDate.of(2027, 11, 25), "THANKSGIVING_DAY"),
            Map.entry(LocalDate.of(2027, 12, 24), "CHRISTMAS_DAY_OBSERVED"),

            Map.entry(LocalDate.of(2028, 1, 17), "MARTIN_LUTHER_KING_JR_DAY"),
            Map.entry(LocalDate.of(2028, 2, 21), "WASHINGTONS_BIRTHDAY"),
            Map.entry(LocalDate.of(2028, 4, 14), "GOOD_FRIDAY"),
            Map.entry(LocalDate.of(2028, 5, 29), "MEMORIAL_DAY"),
            Map.entry(LocalDate.of(2028, 6, 19), "JUNETEENTH"),
            Map.entry(LocalDate.of(2028, 7, 4), "INDEPENDENCE_DAY"),
            Map.entry(LocalDate.of(2028, 9, 4), "LABOR_DAY"),
            Map.entry(LocalDate.of(2028, 11, 23), "THANKSGIVING_DAY"),
            Map.entry(LocalDate.of(2028, 12, 25), "CHRISTMAS_DAY")
    );

    private static final Set<LocalDate> EARLY_CLOSE_DATES = Set.of(
            LocalDate.of(2026, 11, 27),
            LocalDate.of(2026, 12, 24),
            LocalDate.of(2027, 11, 26),
            LocalDate.of(2028, 7, 3),
            LocalDate.of(2028, 11, 24)
    );

    public UsEquityMarketSession sessionFor(LocalDate marketDate) {
        if (!SUPPORTED_YEARS.contains(marketDate.getYear())) {
            return new UsEquityMarketSession(
                    marketDate, false, false, false, "CALENDAR_YEAR_NOT_PUBLISHED", null, null);
        }

        if (marketDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || marketDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new UsEquityMarketSession(
                    marketDate, false, false, true, "WEEKEND", null, null);
        }

        String holiday = HOLIDAYS.get(marketDate);
        if (holiday != null) {
            return new UsEquityMarketSession(
                    marketDate, false, false, true, holiday, null, null);
        }

        boolean earlyClose = EARLY_CLOSE_DATES.contains(marketDate);
        return new UsEquityMarketSession(
                marketDate,
                true,
                earlyClose,
                true,
                earlyClose ? "EARLY_CLOSE" : "REGULAR_SESSION",
                marketDate.atTime(REGULAR_OPEN).atZone(MARKET_ZONE).toInstant(),
                marketDate.atTime(earlyClose ? EARLY_CLOSE : REGULAR_CLOSE).atZone(MARKET_ZONE).toInstant()
        );
    }
}
