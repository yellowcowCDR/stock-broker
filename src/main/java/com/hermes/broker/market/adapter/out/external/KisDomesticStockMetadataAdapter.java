package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import com.hermes.broker.market.application.port.out.LoadStockSectorPort;
import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.trading.domain.MarketType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisDomesticStockMetadataAdapter implements LoadStockSectorPort {

    private static final String PATH = "/uapi/domestic-stock/v1/quotations/search-stock-info";
    private static final String TR_ID = "CTPF1002R";

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final KisRestClientInterceptor kisRestClientInterceptor;
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
    @Cacheable(value = "kis_stock_sector", key = "#stockCode")
    @SuppressWarnings("unchecked")
    public StockSector loadSector(String stockCode) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PATH)
                            .queryParam("PRDT_TYPE_CD", "300")
                            .queryParam("PDNO", stockCode)
                            .build())
                    .headers(headerProvider.createCommonHeaders(TR_ID))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"0".equals(String.valueOf(response.get("rt_cd")))
                    || !(response.get("output") instanceof Map<?, ?>)) {
                String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
                throw new MarketDataUnavailableException("KIS stock metadata lookup failed: " + message);
            }
            return toStockSector(stockCode, (Map<String, Object>) response.get("output"), clock.instant());
        } catch (MarketDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("KIS sector lookup failed for {}", stockCode, e);
            throw new MarketDataUnavailableException("KIS sector lookup failed for " + stockCode + ".", e);
        }
    }

    static StockSector toStockSector(String stockCode, Map<String, Object> output, java.time.Instant fetchedAt) {
        Classification classification = firstClassification(output,
                new Classification("idx_bztp_mcls_cd", "idx_bztp_mcls_cd_name", "INDEX_INDUSTRY_MEDIUM"),
                new Classification("std_idst_clsf_cd", "std_idst_clsf_cd_name", "STANDARD_INDUSTRY"),
                new Classification("idx_bztp_lcls_cd", "idx_bztp_lcls_cd_name", "INDEX_INDUSTRY_LARGE"));

        return new StockSector(
                stockCode,
                MarketType.DOMESTIC,
                text(output.get(classification.codeField())),
                text(output.get(classification.nameField())),
                classification.level(),
                "KIS_OPEN_API:SEARCH_STOCK_INFO",
                fetchedAt,
                true
        );
    }

    private static Classification firstClassification(
            Map<String, Object> output, Classification... classifications) {
        for (Classification classification : classifications) {
            if (!text(output.get(classification.codeField())).isBlank()
                    && !text(output.get(classification.nameField())).isBlank()) {
                return classification;
            }
        }
        throw new MarketDataUnavailableException("KIS stock metadata has no usable industry classification.");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record Classification(String codeField, String nameField, String level) {
    }
}
