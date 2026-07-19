package com.hermes.broker.market.adapter.out.external;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Real KIS quotes for liquid US benchmark ETFs. This is explicitly a benchmark proxy,
 * not an invented full-issue breadth or investor-flow data set.
 */
@Component
@RequiredArgsConstructor
public class KisUsMarketOverviewAdapter implements LoadMarketOverviewPort {

    private static final String PATH = "/uapi/overseas-price/v1/quotations/price";
    private static final String TR_ID = "HHDFS00000300";
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final List<Benchmark> BENCHMARKS = List.of(
            new Benchmark("S&P_500_PROXY", "SPY", "AMS"),
            new Benchmark("NASDAQ_100_PROXY", "QQQ", "NAS"),
            new Benchmark("DOW_30_PROXY", "DIA", "AMS")
    );

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final KisRestClientInterceptor interceptor;
    private final MarketContextProperties properties;
    private final Clock clock;
    private RestClient restClient;

    @PostConstruct
    public void init() {
        restClient = restClientBuilder.baseUrl(kisProperties.baseUrl())
                .requestInterceptor(interceptor)
                .build();
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType == MarketType.OVERSEAS;
    }

    @Override
    @Cacheable(value = "kis_market_overview", key = "'OVERSEAS_US_PROXY'")
    public MarketOverview loadOverview() {
        Instant fetchedAt = clock.instant();
        List<MarketSegmentOverview> segments = new ArrayList<>();
        for (Benchmark benchmark : BENCHMARKS) {
            segments.add(loadBenchmark(benchmark, fetchedAt));
        }
        long advancing = segments.stream().mapToLong(MarketSegmentOverview::advancingIssues).sum();
        long declining = segments.stream().mapToLong(MarketSegmentOverview::decliningIssues).sum();
        long unchanged = segments.stream().mapToLong(MarketSegmentOverview::unchangedIssues).sum();
        BigDecimal score = BigDecimal.valueOf(advancing - declining)
                .divide(BigDecimal.valueOf(BENCHMARKS.size()), 4, RoundingMode.HALF_UP);
        Duration freshness = properties.overviewFreshness() == null
                ? Duration.ofMinutes(5) : properties.overviewFreshness();
        return new MarketOverview(
                MarketType.OVERSEAS,
                List.copyOf(segments),
                advancing,
                declining,
                unchanged,
                score,
                null,
                null,
                null,
                "USD; INVESTOR_FLOW_NOT_AVAILABLE",
                "KIS_OPEN_API:US_BENCHMARK_ETF_PRICE_PROXY:SPY+QQQ+DIA",
                fetchedAt,
                fetchedAt.plus(freshness),
                true,
                "FRESH_BENCHMARK_PROXY"
        );
    }

    private MarketSegmentOverview loadBenchmark(Benchmark benchmark, Instant fetchedAt) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(PATH)
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", benchmark.quoteExchangeCode())
                        .queryParam("SYMB", benchmark.symbol())
                        .build())
                .headers(headerProvider.createCommonHeaders(TR_ID))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !"0".equals(response.path("rt_cd").asText())) {
            String message = response == null ? "empty response" : response.path("msg1").asText();
            throw new MarketDataUnavailableException(
                    "KIS US benchmark quote failed for " + benchmark.symbol() + ": " + message);
        }
        JsonNode output = response.path("output");
        BigDecimal price = requiredDecimal(output, "last");
        BigDecimal changeRate = requiredDecimal(output, "rate").movePointLeft(2);
        BigDecimal tradingValue = requiredDecimal(output, "tamt");
        int sign = changeRate.signum();
        return new MarketSegmentOverview(
                benchmark.segment(),
                benchmark.symbol(),
                price,
                changeRate,
                tradingValue,
                sign > 0 ? 1 : 0,
                sign < 0 ? 1 : 0,
                sign == 0 ? 1 : 0,
                0,
                0,
                BigDecimal.valueOf(sign),
                null,
                null,
                null,
                "USD; BENCHMARK_ETF_ONLY",
                fetchedAt.atZone(NEW_YORK).toLocalDate()
        );
    }

    private BigDecimal requiredDecimal(JsonNode node, String field) {
        String raw = node.path(field).asText("").trim().replace(",", "");
        try {
            BigDecimal value = new BigDecimal(raw);
            if ("last".equals(field) && value.signum() <= 0) {
                throw new NumberFormatException("non-positive price");
            }
            return value;
        } catch (NumberFormatException invalid) {
            throw new MarketDataUnavailableException(
                    "KIS US benchmark quote is missing a valid " + field + ".", invalid);
        }
    }

    private record Benchmark(String segment, String symbol, String quoteExchangeCode) {
    }
}
