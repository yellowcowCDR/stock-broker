package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.market.domain.WatchlistCategory;
import com.hermes.broker.market.domain.WatchlistStock;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KisMarketWatchlistAdapterTest {

    @Test
    void mapsDomesticKisRankingFieldsWithoutSyntheticValues() {
        WatchlistStock result = KisMarketWatchlistAdapter.toDomesticCandidate(Map.of(
                "mksc_shrn_iscd", "005930",
                "hts_kor_isnm", "삼성전자",
                "data_rank", "1",
                "vol_inrt", "170.5",
                "prdy_ctrt", "2.15",
                "acml_tr_pbmn", "1234567890"
        ), 10);

        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.category()).isEqualTo(WatchlistCategory.VOLUME_ANOMALY);
        assertThat(result.reasons()).contains("KIS 거래대금 순위 1", "누적거래대금 1234567890");
    }

    @Test
    void excludesOverseasSymbolThatKisMarksNotOrderable() {
        WatchlistStock result = KisMarketWatchlistAdapter.toOverseasCandidate(Map.of(
                "symb", "AAPL", "name", "APPLE INC", "e_ordyn", "N"
        ), "NAS", 5);

        assertThat(result).isNull();
    }
}
