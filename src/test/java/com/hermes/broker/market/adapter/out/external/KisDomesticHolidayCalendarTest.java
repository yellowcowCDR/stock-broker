package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.property.KisDomesticCalendarProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.ratelimit.KisRateLimitCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisDomesticHolidayCalendarTest {

    private static final String PRODUCTION_BASE_URL = "https://openapi.koreainvestment.com:9443";

    @Test
    void mockTradingEnvironmentUsesProductionCredentialsAndCachesTheTradingDate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisDomesticCalendarProperties calendarProperties = new KisDomesticCalendarProperties(
                true,
                PRODUCTION_BASE_URL,
                "production-calendar-key",
                "production-calendar-secret"
        );
        KisDomesticHolidayCalendar calendar = new KisDomesticHolidayCalendar(
                builder,
                calendarProperties,
                mockKisProperties(),
                mock(KisTokenManager.class),
                noOpRateLimiter(),
                Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC)
        );

        server.expect(once(), requestTo(PRODUCTION_BASE_URL + "/oauth2/tokenP"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"appkey\":\"production-calendar-key\"")))
                .andExpect(content().string(containsString("\"appsecret\":\"production-calendar-secret\"")))
                .andRespond(withSuccess(
                        """
                        {"access_token":"production-calendar-token","expires_in":86400}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo(startsWith(
                        PRODUCTION_BASE_URL + "/uapi/domestic-stock/v1/quotations/chk-holiday")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("authorization", "Bearer production-calendar-token"))
                .andExpect(header("appkey", "production-calendar-key"))
                .andExpect(header("appsecret", "production-calendar-secret"))
                .andExpect(header("tr_id", "CTCA0903R"))
                .andRespond(withSuccess(
                        """
                        {
                          "rt_cd":"0",
                          "msg_cd":"MCA00000",
                          "output":[
                            {"bass_dt":"20260728","bzdy_yn":"Y","opnd_yn":"Y"}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        var first = calendar.sessionFor(LocalDate.of(2026, 7, 28));
        var cached = calendar.sessionFor(LocalDate.of(2026, 7, 28));

        assertTrue(first.complete());
        assertTrue(first.tradingDay());
        assertEquals("KIS_OPEN_DAY", first.reason());
        assertEquals(first, cached);
        server.verify();
    }

    @Test
    void productionTradingEnvironmentReusesTheActiveProductionTokenAndHonorsOpenDayFlag() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisDomesticCalendarProperties calendarProperties = new KisDomesticCalendarProperties(
                true,
                PRODUCTION_BASE_URL,
                "production-calendar-key",
                "production-calendar-secret"
        );
        KisTokenManager activeTokenManager = mock(KisTokenManager.class);
        when(activeTokenManager.getToken()).thenReturn("active-production-token");
        KisDomesticHolidayCalendar calendar = new KisDomesticHolidayCalendar(
                builder,
                calendarProperties,
                productionKisProperties(),
                activeTokenManager,
                noOpRateLimiter(),
                Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC)
        );

        server.expect(once(), requestTo(startsWith(
                        PRODUCTION_BASE_URL + "/uapi/domestic-stock/v1/quotations/chk-holiday")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("authorization", "Bearer active-production-token"))
                .andExpect(header("tr_id", "CTCA0903R"))
                .andRespond(withSuccess(
                        """
                        {
                          "rt_cd":"0",
                          "output":[
                            {"bass_dt":"20260728","bzdy_yn":"Y","opnd_yn":"N"}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        var session = calendar.sessionFor(LocalDate.of(2026, 7, 28));

        assertTrue(session.complete());
        assertFalse(session.tradingDay());
        assertEquals("KIS_NON_OPEN_DAY", session.reason());
        server.verify();
    }

    @Test
    void missingProductionCalendarCredentialsReturnsUnavailableWithoutCallingKis() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisTokenManager tokenManager = mock(KisTokenManager.class);
        KisRateLimitCoordinator rateLimiter = mock(KisRateLimitCoordinator.class);
        KisDomesticHolidayCalendar calendar = new KisDomesticHolidayCalendar(
                builder,
                new KisDomesticCalendarProperties(true, PRODUCTION_BASE_URL, "", ""),
                mockKisProperties(),
                tokenManager,
                rateLimiter,
                Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC)
        );

        var session = calendar.sessionFor(LocalDate.of(2026, 7, 28));

        assertFalse(session.complete());
        assertFalse(session.tradingDay());
        assertEquals("DOMESTIC_HOLIDAY_CALENDAR_CREDENTIALS_MISSING", session.reason());
        verifyNoInteractions(tokenManager, rateLimiter);
        server.verify();
    }

    private KisRateLimitCoordinator noOpRateLimiter() {
        return rateLimitKey -> {
        };
    }

    private KisProperties mockKisProperties() {
        return new KisProperties(
                KisEnvironment.MOCK,
                "https://openapivts.koreainvestment.com:29443",
                null,
                new KisProperties.Api(
                        "mock-order-key",
                        "mock-order-secret",
                        "00000000-01",
                        null,
                        null
                ),
                null,
                null,
                null,
                null,
                null
        );
    }

    private KisProperties productionKisProperties() {
        return new KisProperties(
                KisEnvironment.PRODUCTION,
                PRODUCTION_BASE_URL,
                null,
                new KisProperties.Api(
                        "production-calendar-key",
                        "production-calendar-secret",
                        "12345678-01",
                        null,
                        null
                ),
                null,
                null,
                null,
                null,
                null
        );
    }
}
