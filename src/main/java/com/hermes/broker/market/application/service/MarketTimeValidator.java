package com.hermes.broker.market.application.service;

import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.KisHeaderProvider;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import com.hermes.broker.market.domain.UsEquityMarketSession;
import com.hermes.broker.market.dto.response.MarketStatusResponseDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTimeValidator {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime DOMESTIC_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DOMESTIC_CLOSE = LocalTime.of(15, 30);

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final KisRestClientInterceptor kisRestClientInterceptor;
    private final Clock clock;
    private final UsEquityMarketCalendar usEquityMarketCalendar;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        this.restClient = restClientBuilder
                .baseUrl(kisProperties.baseUrl())
                .requestInterceptor(kisRestClientInterceptor)
                .build();
    }

    public String getEnvironmentName() {
        return kisProperties.environment().name();
    }

    public boolean isDomesticMarketOpen() {
        return getDomesticMarketStatus(clock.instant()).isOpen();
    }

    public void validateMarketOpen(String marketType) {
        MarketStatusResponseDto status = getMarketStatus(marketType);
        if (!status.isComplete()) {
            throw new IllegalStateException("Market calendar is incomplete for " + marketType
                    + ". Reason: " + status.getReason());
        }
        if (!status.isOpen()) {
            throw new IllegalStateException("Market (" + marketType + ") is closed. Status: "
                    + status.getStatus() + ", reason: " + status.getReason());
        }
    }

    public MarketStatusResponseDto getMarketStatus(String marketType) {
        Instant checkedAt = clock.instant();
        try {
            if ("OVERSEAS".equalsIgnoreCase(marketType)) {
                return getOverseasMarketStatus(checkedAt);
            }
            if ("DOMESTIC".equalsIgnoreCase(marketType)) {
                return getDomesticMarketStatus(checkedAt);
            }
            return errorStatus(marketType, checkedAt, "UNSUPPORTED_MARKET_TYPE");
        } catch (Exception e) {
            log.error("Error checking market status for {}", marketType, e);
            return errorStatus(marketType, checkedAt, "MARKET_STATUS_CHECK_FAILED");
        }
    }

    private MarketStatusResponseDto getOverseasMarketStatus(Instant checkedAt) {
        ZonedDateTime marketNow = checkedAt.atZone(UsEquityMarketCalendar.MARKET_ZONE);
        UsEquityMarketSession session = usEquityMarketCalendar.sessionFor(marketNow.toLocalDate());

        if (!session.complete()) {
            return baseStatus("OVERSEAS", checkedAt, marketNow.toLocalDate())
                    .status("CALENDAR_UNAVAILABLE")
                    .reason(session.reason())
                    .complete(false)
                    .calendarSource(UsEquityMarketCalendar.CALENDAR_SOURCE)
                    .build();
        }

        if (!session.tradingDay()) {
            return baseStatus("OVERSEAS", checkedAt, marketNow.toLocalDate())
                    .status("WEEKEND".equals(session.reason()) ? "CLOSED_WEEKEND" : "CLOSED_HOLIDAY")
                    .reason(session.reason())
                    .complete(true)
                    .calendarSource(UsEquityMarketCalendar.CALENDAR_SOURCE)
                    .build();
        }

        boolean open = !checkedAt.isBefore(session.opensAt()) && checkedAt.isBefore(session.closesAt());
        String status;
        if (open) {
            status = session.earlyClose() ? "REGULAR_MARKET_EARLY_CLOSE" : "REGULAR_MARKET";
        } else if (checkedAt.isBefore(session.opensAt())) {
            status = marketNow.toLocalTime().isBefore(LocalTime.of(4, 0)) ? "CLOSED" : "PRE_MARKET";
        } else {
            status = session.earlyClose() ? "CLOSED_EARLY" :
                    (marketNow.toLocalTime().isBefore(LocalTime.of(20, 0)) ? "AFTER_MARKET" : "CLOSED");
        }

        return baseStatus("OVERSEAS", checkedAt, marketNow.toLocalDate())
                .isOpen(open)
                .status(status)
                .reason(session.reason())
                .earlyClose(session.earlyClose())
                .complete(true)
                .calendarSource(UsEquityMarketCalendar.CALENDAR_SOURCE)
                .sessionOpensAt(session.opensAt())
                .sessionClosesAt(session.closesAt())
                .build();
    }

    private MarketStatusResponseDto getDomesticMarketStatus(Instant checkedAt) {
        ZonedDateTime seoulNow = checkedAt.atZone(SEOUL);
        LocalDate marketDate = seoulNow.toLocalDate();
        DayOfWeek day = seoulNow.getDayOfWeek();
        LocalTime time = seoulNow.toLocalTime();
        Instant opensAt = marketDate.atTime(DOMESTIC_OPEN).atZone(SEOUL).toInstant();
        Instant closesAt = marketDate.atTime(DOMESTIC_CLOSE).atZone(SEOUL).toInstant();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return baseStatus("DOMESTIC", checkedAt, marketDate)
                    .status("CLOSED_WEEKEND")
                    .reason("WEEKEND")
                    .complete(true)
                    .calendarSource("KIS_DOMESTIC_HOLIDAY_API")
                    .build();
        }
        if (time.isBefore(DOMESTIC_OPEN) || !time.isBefore(DOMESTIC_CLOSE)) {
            return baseStatus("DOMESTIC", checkedAt, marketDate)
                    .status("CLOSED")
                    .reason("OUTSIDE_REGULAR_SESSION")
                    .complete(true)
                    .calendarSource("KIS_DOMESTIC_HOLIDAY_API")
                    .sessionOpensAt(opensAt)
                    .sessionClosesAt(closesAt)
                    .build();
        }

        if (kisProperties.baseUrl().contains("openapivts")) {
            log.warn("KIS mock environment does not provide a verified domestic holiday calendar; blocking orders fail-closed.");
            return baseStatus("DOMESTIC", checkedAt, marketDate)
                    .status("CALENDAR_UNAVAILABLE")
                    .reason("DOMESTIC_HOLIDAY_CALENDAR_UNAVAILABLE_IN_MOCK")
                    .complete(false)
                    .calendarSource("KIS_DOMESTIC_HOLIDAY_API")
                    .sessionOpensAt(opensAt)
                    .sessionClosesAt(closesAt)
                    .build();
        }

        if (!isDomesticBusinessDay(marketDate)) {
            return baseStatus("DOMESTIC", checkedAt, marketDate)
                    .status("CLOSED_HOLIDAY")
                    .reason("KIS_NON_BUSINESS_DAY")
                    .complete(true)
                    .calendarSource("KIS_DOMESTIC_HOLIDAY_API")
                    .build();
        }

        return baseStatus("DOMESTIC", checkedAt, marketDate)
                .isOpen(true)
                .status("REGULAR_MARKET")
                .reason("REGULAR_SESSION")
                .complete(true)
                .calendarSource("KIS_DOMESTIC_HOLIDAY_API")
                .sessionOpensAt(opensAt)
                .sessionClosesAt(closesAt)
                .build();
    }

    @SuppressWarnings("unchecked")
    private boolean isDomesticBusinessDay(LocalDate marketDate) {
        String date = marketDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/chk-holiday")
                            .queryParam("BASS_DT", date)
                            .queryParam("CTX_AREA_NK", "")
                            .queryParam("CTX_AREA_FK", "")
                            .build())
                    .headers(headerProvider.createCommonHeaders("CTCA0903R"))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("output") instanceof List<?> output) || output.isEmpty()) {
                throw new IllegalStateException("KIS holiday response is incomplete");
            }
            Map<String, String> day = (Map<String, String>) output.get(0);
            return "Y".equalsIgnoreCase(day.get("bzdy_yn"))
                    && !"N".equalsIgnoreCase(day.get("opnd_yn"));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot verify domestic market holiday", e);
        }
    }

    private MarketStatusResponseDto.MarketStatusResponseDtoBuilder baseStatus(
            String marketType, Instant checkedAt, LocalDate marketDate) {
        ZoneId marketZone = "OVERSEAS".equals(marketType) ? UsEquityMarketCalendar.MARKET_ZONE : SEOUL;
        return MarketStatusResponseDto.builder()
                .marketType(marketType)
                .isOpen(false)
                .marketTimeZone(marketZone.getId())
                .marketDate(marketDate)
                .earlyClose(false)
                .checkedAt(checkedAt);
    }

    private MarketStatusResponseDto errorStatus(String marketType, Instant checkedAt, String reason) {
        return MarketStatusResponseDto.builder()
                .marketType(marketType)
                .isOpen(false)
                .status("ERROR")
                .reason(reason)
                .marketTimeZone(UTC.getId())
                .complete(false)
                .checkedAt(checkedAt)
                .build();
    }
}
