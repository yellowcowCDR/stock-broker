package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.domain.StockSector;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisDomesticStockMetadataAdapterTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-07-19T00:00:00Z");

    @Test
    void prefersMediumIndexIndustryForConcentrationChecks() {
        StockSector sector = KisDomesticStockMetadataAdapter.toStockSector("005930", Map.of(
                "idx_bztp_lcls_cd", "001",
                "idx_bztp_lcls_cd_name", "제조업",
                "idx_bztp_mcls_cd", "013",
                "idx_bztp_mcls_cd_name", "전기전자",
                "std_idst_clsf_cd", "26110",
                "std_idst_clsf_cd_name", "반도체 제조업"
        ), FETCHED_AT);

        assertThat(sector.sectorCode()).isEqualTo("013");
        assertThat(sector.sectorName()).isEqualTo("전기전자");
        assertThat(sector.classificationLevel()).isEqualTo("INDEX_INDUSTRY_MEDIUM");
        assertThat(sector.dataSource()).isEqualTo("KIS_OPEN_API:SEARCH_STOCK_INFO");
        assertThat(sector.complete()).isTrue();
    }

    @Test
    void fallsBackToStandardIndustryWhenIndexIndustryIsMissing() {
        StockSector sector = KisDomesticStockMetadataAdapter.toStockSector("000000", Map.of(
                "std_idst_clsf_cd", "64992",
                "std_idst_clsf_cd_name", "투자기관"
        ), FETCHED_AT);

        assertThat(sector.sectorName()).isEqualTo("투자기관");
        assertThat(sector.classificationLevel()).isEqualTo("STANDARD_INDUSTRY");
    }

    @Test
    void missingRealIndustryDataFailsClosed() {
        assertThatThrownBy(() -> KisDomesticStockMetadataAdapter.toStockSector(
                "005930", Map.of(), FETCHED_AT))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
