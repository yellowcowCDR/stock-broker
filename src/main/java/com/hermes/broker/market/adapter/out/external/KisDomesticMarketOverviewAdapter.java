package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.MarketContextProperties;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import com.hermes.broker.market.application.port.out.LoadMarketOverviewPort;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.market.domain.MarketSegmentOverview;
import com.hermes.broker.trading.domain.MarketType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisDomesticMarketOverviewAdapter implements LoadMarketOverviewPort {

    private static final String INDEX_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-index-category-price";
    private static final String INDEX_TR_ID = "FHPUP02140000";
    private static final String INVESTOR_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-investor-daily-by-market";
    private static final String INVESTOR_TR_ID = "FHPTJ04040000";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String VALUE_UNIT = "KIS_API_NATIVE";
    private static final String DATA_SOURCE =
            "KIS_OPEN_API:INDEX_CATEGORY_PRICE+INVESTOR_DAILY_BY_MARKET";
    private static final List<SegmentDefinition> SEGMENTS = List.of(
            new SegmentDefinition("KOSPI", "0001", "K", "KSP"),
            new SegmentDefinition("KOSDAQ", "1001", "Q", "KSQ")
    );

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final KisRestClientInterceptor kisRestClientInterceptor;
    private final MarketContextProperties properties;
    private final Clock clock;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        restClient = restClientBuilder
                .baseUrl(kisProperties.baseUrl())
                .requestInterceptor(kisRestClientInterceptor)
                .build();
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType == MarketType.DOMESTIC;
    }

    @Override
    @Cacheable(value = "kis_market_overview", key = "'DOMESTIC'")
    public MarketOverview loadOverview() {
        Instant fetchedAt = clock.instant();
        LocalDate marketDate = fetchedAt.atZone(SEOUL).toLocalDate();
        try {
            List<MarketSegmentOverview> segments = new ArrayList<>();
            for (SegmentDefinition definition : SEGMENTS) {
                Map<String, String> breadth = loadBreadth(definition);
                Map<String, String> investor = loadInvestorFlow(definition, marketDate);
                segments.add(toSegmentOverview(definition, breadth, investor, marketDate));
            }

            long advancing = segments.stream().mapToLong(MarketSegmentOverview::advancingIssues).sum();
            long declining = segments.stream().mapToLong(MarketSegmentOverview::decliningIssues).sum();
            long unchanged = segments.stream().mapToLong(MarketSegmentOverview::unchangedIssues).sum();
            BigDecimal foreign = sum(segments, MarketSegmentOverview::foreignNetBuyTradingValue);
            BigDecimal individual = sum(segments, MarketSegmentOverview::individualNetBuyTradingValue);
            BigDecimal institution = sum(segments, MarketSegmentOverview::institutionNetBuyTradingValue);
            Duration freshness = properties.overviewFreshness() == null
                    ? Duration.ofMinutes(5) : properties.overviewFreshness();

            return new MarketOverview(
                    MarketType.DOMESTIC,
                    List.copyOf(segments),
                    advancing,
                    declining,
                    unchanged,
                    breadthScore(advancing, declining, unchanged),
                    foreign,
                    individual,
                    institution,
                    VALUE_UNIT,
                    DATA_SOURCE,
                    fetchedAt,
                    fetchedAt.plus(freshness),
                    true,
                    "FRESH"
            );
        } catch (MarketDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("KIS domestic market overview lookup failed", e);
            throw new MarketDataUnavailableException("KIS domestic market overview lookup failed.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadBreadth(SegmentDefinition definition) {
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(INDEX_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", definition.indexCode())
                        .queryParam("FID_COND_SCR_DIV_CODE", "20214")
                        .queryParam("FID_MRKT_CLS_CODE", definition.marketClassCode())
                        .queryParam("FID_BLNG_CLS_CODE", "0")
                        .build())
                .headers(headerProvider.createCommonHeaders(INDEX_TR_ID))
                .retrieve()
                .body(Map.class);
        return requiredFirstRow(response, "output1", definition.segment() + " breadth");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadInvestorFlow(
            SegmentDefinition definition, LocalDate marketDate) {
        String date = marketDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(INVESTOR_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", definition.indexCode())
                        .queryParam("FID_INPUT_DATE_1", date)
                        .queryParam("FID_INPUT_ISCD_1", definition.investorMarketCode())
                        .queryParam("FID_INPUT_DATE_2", date)
                        .queryParam("FID_INPUT_ISCD_2", definition.indexCode())
                        .build())
                .headers(headerProvider.createCommonHeaders(INVESTOR_TR_ID))
                .retrieve()
                .body(Map.class);

        List<Map<String, String>> rows = requiredRows(
                response, "output", definition.segment() + " investor flow");
        return rows.stream()
                .filter(row -> date.equals(text(row.get("stck_bsop_date"))))
                .findFirst()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "KIS " + definition.segment() + " investor flow has no row for " + date + "."));
    }

    static MarketSegmentOverview toSegmentOverview(
            SegmentDefinition definition,
            Map<String, String> breadth,
            Map<String, String> investor,
            LocalDate expectedMarketDate) {
        LocalDate observedDate;
        try {
            observedDate = LocalDate.parse(requiredText(investor, "stck_bsop_date"),
                    DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception e) {
            throw new MarketDataUnavailableException("KIS investor-flow market date is invalid.", e);
        }
        if (!expectedMarketDate.equals(observedDate)) {
            throw new MarketDataUnavailableException(
                    "KIS investor-flow data is stale for " + definition.segment() + ".");
        }

        long advancing = requiredLong(breadth, "ascn_issu_cnt");
        long declining = requiredLong(breadth, "down_issu_cnt");
        long unchanged = requiredLong(breadth, "stnr_issu_cnt");
        return new MarketSegmentOverview(
                definition.segment(),
                definition.indexCode(),
                requiredDecimal(breadth, "bstp_nmix_prpr"),
                requiredDecimal(breadth, "bstp_nmix_prdy_ctrt").movePointLeft(2),
                requiredDecimal(breadth, "acml_tr_pbmn"),
                advancing,
                declining,
                unchanged,
                requiredLong(breadth, "uplm_issu_cnt"),
                requiredLong(breadth, "lslm_issu_cnt"),
                breadthScore(advancing, declining, unchanged),
                requiredDecimal(investor, "frgn_ntby_tr_pbmn"),
                requiredDecimal(investor, "prsn_ntby_tr_pbmn"),
                requiredDecimal(investor, "orgn_ntby_tr_pbmn"),
                VALUE_UNIT,
                observedDate
        );
    }

    private static BigDecimal breadthScore(long advancing, long declining, long unchanged) {
        long total = advancing + declining + unchanged;
        if (total <= 0) {
            throw new MarketDataUnavailableException("KIS market breadth contains no issues.");
        }
        return BigDecimal.valueOf(advancing - declining)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(
            List<MarketSegmentOverview> segments,
            java.util.function.Function<MarketSegmentOverview, BigDecimal> extractor) {
        return segments.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, String> requiredFirstRow(
            Map<String, Object> response, String key, String description) {
        validateResponse(response, description);
        Object output = response.get(key);
        if (output instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (output instanceof List<?> rows && !rows.isEmpty() && rows.get(0) instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        throw new MarketDataUnavailableException("KIS " + description + " response has no row.");
    }

    private static List<Map<String, String>> requiredRows(
            Map<String, Object> response, String key, String description) {
        validateResponse(response, description);
        Object output = response.get(key);
        if (!(output instanceof List<?> rows) || rows.isEmpty()) {
            throw new MarketDataUnavailableException("KIS " + description + " response has no rows.");
        }
        return rows.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(KisDomesticMarketOverviewAdapter::stringMap)
                .toList();
    }

    private static void validateResponse(Map<String, Object> response, String description) {
        if (response == null || !"0".equals(String.valueOf(response.get("rt_cd")))) {
            String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
            throw new MarketDataUnavailableException("KIS " + description + " failed: " + message);
        }
    }

    private static Map<String, String> stringMap(Map<?, ?> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
        return result;
    }

    private static long requiredLong(Map<String, String> row, String key) {
        try {
            return requiredDecimal(row, key).longValueExact();
        } catch (ArithmeticException e) {
            throw new MarketDataUnavailableException("KIS field is not an integer: " + key, e);
        }
    }

    private static BigDecimal requiredDecimal(Map<String, String> row, String key) {
        String value = requiredText(row, key).replace(",", "");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new MarketDataUnavailableException("KIS field is not numeric: " + key, e);
        }
    }

    private static String requiredText(Map<String, String> row, String key) {
        String value = text(row.get(key));
        if (value.isBlank()) {
            throw new MarketDataUnavailableException("KIS response is missing field: " + key);
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record SegmentDefinition(
            String segment,
            String indexCode,
            String marketClassCode,
            String investorMarketCode
    ) {
    }
}
