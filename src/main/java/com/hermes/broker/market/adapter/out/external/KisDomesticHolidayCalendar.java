package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.property.KisDomesticCalendarProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.ratelimit.KisRateLimitCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class KisDomesticHolidayCalendar {

    public static final String CALENDAR_SOURCE = "KIS_DOMESTIC_HOLIDAY_API";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String PRODUCTION_RATE_LIMIT_KEY = "PRODUCTION:domestic-holiday-calendar";
    private static final Duration FAILED_LOOKUP_CACHE_TTL = Duration.ofMinutes(1);
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(23);
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final KisDomesticCalendarProperties calendarProperties;
    private final KisProperties activeKisProperties;
    private final KisTokenManager activeTokenManager;
    private final KisRateLimitCoordinator rateLimitCoordinator;
    private final Clock clock;
    private final ConcurrentHashMap<LocalDate, CacheEntry> sessionCache = new ConcurrentHashMap<>();
    private final ReentrantLock lookupLock = new ReentrantLock();
    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile CalendarToken calendarToken;

    public KisDomesticHolidayCalendar(
            RestClient.Builder restClientBuilder,
            KisDomesticCalendarProperties calendarProperties,
            KisProperties activeKisProperties,
            KisTokenManager activeTokenManager,
            KisRateLimitCoordinator rateLimitCoordinator,
            Clock clock
    ) {
        this.calendarProperties = calendarProperties;
        this.activeKisProperties = activeKisProperties;
        this.activeTokenManager = activeTokenManager;
        this.rateLimitCoordinator = rateLimitCoordinator;
        this.clock = clock;
        this.restClient = restClientBuilder
                .baseUrl(calendarProperties.baseUrl())
                .build();
    }

    public DomesticHolidaySession sessionFor(LocalDate marketDate) {
        Instant now = clock.instant();
        CacheEntry cached = sessionCache.get(marketDate);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.session();
        }

        lookupLock.lock();
        try {
            now = clock.instant();
            cached = sessionCache.get(marketDate);
            if (cached != null && now.isBefore(cached.expiresAt())) {
                return cached.session();
            }

            DomesticHolidaySession session = loadSession(marketDate);
            Instant expiresAt = session.complete()
                    ? marketDate.plusDays(1).atStartOfDay(SEOUL).toInstant()
                    : now.plus(FAILED_LOOKUP_CACHE_TTL);
            sessionCache.put(marketDate, new CacheEntry(session, expiresAt));
            return session;
        } finally {
            lookupLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private DomesticHolidaySession loadSession(LocalDate marketDate) {
        if (!calendarProperties.enabled()) {
            return DomesticHolidaySession.unavailable("DOMESTIC_HOLIDAY_CALENDAR_DISABLED");
        }
        if (!hasUsableCalendarCredentials()) {
            log.warn("KIS production credentials are missing; domestic holiday lookup is unavailable.");
            return DomesticHolidaySession.unavailable("DOMESTIC_HOLIDAY_CALENDAR_CREDENTIALS_MISSING");
        }

        String date = marketDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        try {
            rateLimitCoordinator.acquire(PRODUCTION_RATE_LIMIT_KEY);
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/chk-holiday")
                            .queryParam("BASS_DT", date)
                            .queryParam("CTX_AREA_NK", "")
                            .queryParam("CTX_AREA_FK", "")
                            .build())
                    .headers(headers -> {
                        headers.setBearerAuth(getProductionToken());
                        headers.set("appkey", calendarProperties.appKey());
                        headers.set("appsecret", calendarProperties.appSecret());
                        headers.set("tr_id", "CTCA0903R");
                        headers.set("custtype", "P");
                    })
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"0".equals(String.valueOf(response.get("rt_cd")))) {
                log.warn("KIS domestic holiday response reported failure: rt_cd={}, msg_cd={}",
                        response == null ? null : response.get("rt_cd"),
                        response == null ? null : response.get("msg_cd"));
                return DomesticHolidaySession.unavailable("DOMESTIC_HOLIDAY_CALENDAR_RESPONSE_FAILED");
            }
            if (!(response.get("output") instanceof List<?> output)) {
                return DomesticHolidaySession.unavailable("DOMESTIC_HOLIDAY_CALENDAR_RESPONSE_INCOMPLETE");
            }

            Map<String, Object> day = output.stream()
                    .filter(Map.class::isInstance)
                    .map(value -> (Map<String, Object>) value)
                    .filter(value -> date.equals(String.valueOf(value.get("bass_dt"))))
                    .findFirst()
                    .orElse(null);
            if (day == null || day.get("opnd_yn") == null) {
                return DomesticHolidaySession.unavailable("DOMESTIC_HOLIDAY_CALENDAR_DATE_MISSING");
            }

            boolean tradingDay = "Y".equalsIgnoreCase(String.valueOf(day.get("opnd_yn")));
            return new DomesticHolidaySession(
                    true,
                    tradingDay,
                    tradingDay ? "KIS_OPEN_DAY" : "KIS_NON_OPEN_DAY"
            );
        } catch (Exception exception) {
            log.warn("KIS domestic holiday lookup failed for {}", marketDate, exception);
            return DomesticHolidaySession.unavailable("DOMESTIC_HOLIDAY_CALENDAR_LOOKUP_FAILED");
        }
    }

    private String getProductionToken() {
        if (canReuseActiveProductionToken()) {
            return activeTokenManager.getToken();
        }

        Instant now = clock.instant();
        CalendarToken token = calendarToken;
        if (token != null && now.isBefore(token.expiresAt().minus(TOKEN_REFRESH_MARGIN))) {
            return token.value();
        }

        tokenLock.lock();
        try {
            now = clock.instant();
            token = calendarToken;
            if (token != null && now.isBefore(token.expiresAt().minus(TOKEN_REFRESH_MARGIN))) {
                return token.value();
            }
            calendarToken = issueProductionToken(now);
            return calendarToken.value();
        } finally {
            tokenLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private CalendarToken issueProductionToken(Instant issuedAt) {
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", calendarProperties.appKey(),
                "appsecret", calendarProperties.appSecret()
        );
        Map<String, Object> response = restClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("KIS production token response is incomplete");
        }
        long expiresInSeconds = parseExpiresIn(response.get("expires_in"));
        return new CalendarToken(
                String.valueOf(response.get("access_token")),
                issuedAt.plusSeconds(expiresInSeconds)
        );
    }

    private long parseExpiresIn(Object expiresIn) {
        if (expiresIn instanceof Number number) {
            return Math.max(60, number.longValue());
        }
        if (expiresIn != null) {
            try {
                return Math.max(60, Long.parseLong(String.valueOf(expiresIn)));
            } catch (NumberFormatException ignored) {
                // Use the conservative fallback below.
            }
        }
        return DEFAULT_TOKEN_TTL.toSeconds();
    }

    private boolean canReuseActiveProductionToken() {
        return activeKisProperties.environment() == KisEnvironment.PRODUCTION
                && Objects.equals(activeKisProperties.baseUrl(), calendarProperties.baseUrl())
                && Objects.equals(activeKisProperties.api().appKey(), calendarProperties.appKey())
                && Objects.equals(activeKisProperties.api().appSecret(), calendarProperties.appSecret());
    }

    private boolean hasUsableCalendarCredentials() {
        return !isPlaceholder(calendarProperties.appKey())
                && !isPlaceholder(calendarProperties.appSecret());
    }

    private boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || value.startsWith("your_");
    }

    public record DomesticHolidaySession(
            boolean complete,
            boolean tradingDay,
            String reason
    ) {
        public static DomesticHolidaySession unavailable(String reason) {
            return new DomesticHolidaySession(false, false, reason);
        }
    }

    private record CacheEntry(DomesticHolidaySession session, Instant expiresAt) {
    }

    private record CalendarToken(String value, Instant expiresAt) {
    }
}
