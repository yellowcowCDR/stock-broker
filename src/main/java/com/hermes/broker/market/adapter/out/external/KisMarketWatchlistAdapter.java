package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.MarketWatchlistProperties;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import com.hermes.broker.market.application.port.out.LoadMarketWatchlistPort;
import com.hermes.broker.market.domain.MarketWatchlistResult;
import com.hermes.broker.market.domain.WatchlistCategory;
import com.hermes.broker.market.domain.WatchlistStock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisMarketWatchlistAdapter implements LoadMarketWatchlistPort {

    private static final String DOMESTIC_PATH = "/uapi/domestic-stock/v1/quotations/volume-rank";
    private static final String DOMESTIC_TR_ID = "FHPST01710000";
    private static final String OVERSEAS_PATH = "/uapi/overseas-stock/v1/ranking/trade-pbmn";
    private static final String OVERSEAS_TR_ID = "HHDFS76320010";
    private static final List<String> US_EXCHANGES = List.of("NAS", "NYS", "AMS");

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final KisRestClientInterceptor kisRestClientInterceptor;
    private final MarketWatchlistProperties properties;
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
    public MarketWatchlistResult loadCandidates() {
        try {
            List<WatchlistStock> candidates = new ArrayList<>(loadDomesticCandidates());
            for (String exchange : US_EXCHANGES) {
                candidates.addAll(loadOverseasCandidates(exchange));
            }

            Map<String, WatchlistStock> unique = new LinkedHashMap<>();
            candidates.stream()
                    .sorted(Comparator.comparing(WatchlistStock::score).reversed())
                    .forEach(candidate -> unique.putIfAbsent(
                            candidate.market() + ":" + candidate.stockCode(), candidate));

            List<WatchlistStock> selected = unique.values().stream()
                    .limit(positive(properties.maxCandidates(), 25))
                    .toList();
            if (selected.isEmpty()) {
                throw new MarketDataUnavailableException("KIS ranking APIs returned no orderable candidates.");
            }

            return new MarketWatchlistResult(
                    selected,
                    "KIS_OPEN_API:DOMESTIC_VOLUME_RANK+US_TRADE_AMOUNT_RANK",
                    clock.instant(),
                    true,
                    "FRESH",
                    true
            );
        } catch (MarketDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to build watchlist from KIS real market ranking APIs", e);
            throw new MarketDataUnavailableException("KIS real-market watchlist lookup failed.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<WatchlistStock> loadDomesticCandidates() {
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(DOMESTIC_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_COND_SCR_DIV_CODE", "20171")
                        .queryParam("FID_INPUT_ISCD", "0000")
                        .queryParam("FID_DIV_CLS_CODE", "1")
                        .queryParam("FID_BLNG_CLS_CODE", "3")
                        .queryParam("FID_TRGT_CLS_CODE", "111111111")
                        .queryParam("FID_TRGT_EXLS_CLS_CODE", "1111111111")
                        .queryParam("FID_INPUT_PRICE_1", "0")
                        .queryParam("FID_INPUT_PRICE_2", "0")
                        .queryParam("FID_VOL_CNT", Math.max(properties.minDomesticVolume(), 0))
                        .queryParam("FID_INPUT_DATE_1", "")
                        .build())
                .headers(headerProvider.createCommonHeaders(DOMESTIC_TR_ID))
                .retrieve()
                .body(Map.class);

        List<Map<String, String>> rows = requiredRows(response, "output", "domestic volume rank");
        int limit = positive(properties.domesticLimit(), 10);
        List<WatchlistStock> result = new ArrayList<>();
        for (int index = 0; index < rows.size() && result.size() < limit; index++) {
            WatchlistStock stock = toDomesticCandidate(rows.get(index), limit);
            if (stock != null) {
                result.add(stock);
            }
        }
        if (result.isEmpty()) {
            throw new MarketDataUnavailableException("KIS domestic ranking returned no usable common stocks.");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<WatchlistStock> loadOverseasCandidates(String exchange) {
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(OVERSEAS_PATH)
                        .queryParam("EXCD", exchange)
                        .queryParam("NDAY", "0")
                        .queryParam("VOL_RANG", "0")
                        .queryParam("AUTH", "")
                        .queryParam("KEYB", "")
                        .queryParam("PRC1", "")
                        .queryParam("PRC2", "")
                        .build())
                .headers(headerProvider.createCommonHeaders(OVERSEAS_TR_ID))
                .retrieve()
                .body(Map.class);

        List<Map<String, String>> rows = requiredRows(response, "output2", exchange + " trade amount rank");
        int limit = positive(properties.overseasPerExchangeLimit(), 5);
        List<WatchlistStock> result = new ArrayList<>();
        for (int index = 0; index < rows.size() && result.size() < limit; index++) {
            WatchlistStock stock = toOverseasCandidate(rows.get(index), exchange, limit);
            if (stock != null) {
                result.add(stock);
            }
        }
        if (result.isEmpty()) {
            throw new MarketDataUnavailableException("KIS " + exchange + " ranking returned no orderable stocks.");
        }
        return result;
    }

    private List<Map<String, String>> requiredRows(
            Map<String, Object> response, String outputKey, String sourceName) {
        if (response == null || !"0".equals(String.valueOf(response.get("rt_cd")))) {
            String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
            throw new MarketDataUnavailableException("KIS " + sourceName + " failed: " + message);
        }
        Object output = response.get(outputKey);
        if (!(output instanceof List<?> rows) || rows.isEmpty()) {
            throw new MarketDataUnavailableException("KIS " + sourceName + " response has no rows.");
        }
        return rows.stream()
                .filter(Map.class::isInstance)
                .map(row -> (Map<String, String>) row)
                .toList();
    }

    static WatchlistStock toDomesticCandidate(Map<String, String> row, int limit) {
        String code = text(row, "mksc_shrn_iscd");
        String name = text(row, "hts_kor_isnm");
        if (code.isBlank() || name.isBlank()) {
            return null;
        }
        int rank = requiredInteger(row, "data_rank");
        BigDecimal volumeIncrease = requiredDecimal(row, "vol_inrt");
        BigDecimal changeRate = requiredDecimal(row, "prdy_ctrt");
        WatchlistCategory category = volumeIncrease.compareTo(new BigDecimal("150")) >= 0
                ? WatchlistCategory.VOLUME_ANOMALY : WatchlistCategory.MOMENTUM;
        return new WatchlistStock(
                code,
                name,
                "KRX",
                category,
                rankScore(rank, limit),
                List.of(
                        "KIS 거래대금 순위 " + rank,
                        "누적거래대금 " + requiredText(row, "acml_tr_pbmn"),
                        "거래량증가율 " + volumeIncrease.toPlainString() + "%",
                        "등락률 " + changeRate.toPlainString() + "%"
                )
        );
    }

    static WatchlistStock toOverseasCandidate(
            Map<String, String> row, String exchange, int limit) {
        String orderable = text(row, "e_ordyn");
        if (!orderable.isBlank() && !"Y".equalsIgnoreCase(orderable)) {
            return null;
        }
        String code = text(row, "symb");
        String name = text(row, "name");
        if (code.isBlank() || name.isBlank()) {
            return null;
        }
        int rank = requiredInteger(row, "rank");
        BigDecimal changeRate = requiredDecimal(row, "rate");
        return new WatchlistStock(
                code,
                name,
                marketName(exchange),
                WatchlistCategory.MOMENTUM,
                rankScore(rank, limit),
                List.of(
                        "KIS 거래대금 순위 " + rank,
                        "거래대금 " + requiredText(row, "tamt"),
                        "거래량 " + requiredText(row, "tvol"),
                        "등락률 " + changeRate.toPlainString() + "%",
                        "KIS 매매가능 여부 Y"
                )
        );
    }

    private static BigDecimal rankScore(int rank, int limit) {
        int normalizedLimit = Math.max(limit, 1);
        int normalizedRank = Math.max(rank, 1);
        BigDecimal step = new BigDecimal("50").divide(
                BigDecimal.valueOf(normalizedLimit), 4, RoundingMode.HALF_UP);
        return new BigDecimal("100")
                .subtract(step.multiply(BigDecimal.valueOf(normalizedRank - 1L)))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String marketName(String exchange) {
        return switch (exchange) {
            case "NAS" -> "NASDAQ";
            case "NYS" -> "NYSE";
            case "AMS" -> "AMEX";
            default -> exchange;
        };
    }

    private static String text(Map<String, String> row, String key) {
        String value = row.get(key);
        return value == null ? "" : value.trim();
    }

    private static BigDecimal requiredDecimal(Map<String, String> row, String key) {
        String value = requiredText(row, key);
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new MarketDataUnavailableException("KIS ranking field is not numeric: " + key, e);
        }
    }

    private static String requiredText(Map<String, String> row, String key) {
        String value = text(row, key);
        if (value.isBlank()) {
            throw new MarketDataUnavailableException("KIS ranking response is missing field: " + key);
        }
        return value;
    }

    private static int requiredInteger(Map<String, String> row, String key) {
        String value = requiredText(row, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new MarketDataUnavailableException("KIS ranking field is not an integer: " + key, e);
        }
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
