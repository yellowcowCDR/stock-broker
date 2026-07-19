package com.hermes.broker.market.adapter.out.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.exception.ExternalApiNotConfiguredException;
import com.hermes.broker.common.property.AlphaVantageProperties;
import com.hermes.broker.market.application.port.out.LoadUsFundamentalsPort;
import com.hermes.broker.market.domain.UsFundamentalsSnapshot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageFundamentalsAdapter implements LoadUsFundamentalsPort {

    private final AlphaVantageProperties properties;
    private final AlphaVantageResponseParser parser;
    private final Clock clock;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("Alpha Vantage API is disabled or not configured.");
            return;
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());
        restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @Cacheable(value = "alpha_vantage_us_fundamentals", key = "#symbol")
    public UsFundamentalsSnapshot load(String symbol) {
        if (restClient == null) {
            throw new ExternalApiNotConfiguredException("Alpha Vantage API is not configured.");
        }
        try {
            JsonNode overview = getJson("OVERVIEW", symbol);
            JsonNode income = getJson("INCOME_STATEMENT", symbol);
            JsonNode balance = getJson("BALANCE_SHEET", symbol);
            JsonNode cashFlow = getJson("CASH_FLOW", symbol);
            JsonNode earnings = getJson("EARNINGS", symbol);
            String calendar = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "EARNINGS_CALENDAR")
                            .queryParam("symbol", symbol)
                            .queryParam("horizon", properties.earningsHorizon())
                            .queryParam("apikey", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(String.class);

            return parser.parse(
                    symbol,
                    overview,
                    income,
                    balance,
                    cashFlow,
                    earnings,
                    calendar,
                    clock.instant(),
                    properties.freshnessThreshold()
            );
        } catch (DataPipelineUnavailableException | ExternalApiNotConfiguredException expected) {
            throw expected;
        } catch (Exception failure) {
            log.error("Alpha Vantage fundamentals request failed for {}", symbol, failure);
            throw new DataPipelineUnavailableException(
                    "Failed to fetch complete Alpha Vantage fundamentals for " + symbol + "."
            );
        }
    }

    private JsonNode getJson(String function, String symbol) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", function)
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", properties.apiKey())
                        .build())
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new DataPipelineUnavailableException(
                    "Alpha Vantage " + function + " returned an empty response."
            );
        }
        return response;
    }
}
