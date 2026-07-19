package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.domain.MarketSegmentOverview;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisDomesticMarketOverviewAdapterTest {

    private static final LocalDate MARKET_DATE = LocalDate.of(2026, 7, 20);

    @Test
    void mapsOnlyOfficialBreadthAndInvestorFields() {
        MarketSegmentOverview result = KisDomesticMarketOverviewAdapter.toSegmentOverview(
                new KisDomesticMarketOverviewAdapter.SegmentDefinition("KOSPI", "0001", "K", "KSP"),
                breadth(),
                investor(),
                MARKET_DATE);

        assertThat(result.segment()).isEqualTo("KOSPI");
        assertThat(result.indexChangeRate()).isEqualByComparingTo("0.0123");
        assertThat(result.breadthScore()).isEqualByComparingTo("0.3000");
        assertThat(result.foreignNetBuyTradingValue()).isEqualByComparingTo("120000");
        assertThat(result.tradingValueUnit()).isEqualTo("KIS_API_NATIVE");
        assertThat(result.observedMarketDate()).isEqualTo(MARKET_DATE);
    }

    @Test
    void staleInvestorDateFailsClosed() {
        Map<String, String> stale = new HashMap<>(investor());
        stale.put("stck_bsop_date", "20260717");

        assertThatThrownBy(() -> KisDomesticMarketOverviewAdapter.toSegmentOverview(
                new KisDomesticMarketOverviewAdapter.SegmentDefinition("KOSPI", "0001", "K", "KSP"),
                breadth(), stale, MARKET_DATE))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("stale");
    }

    @Test
    void missingBreadthFieldFailsClosed() {
        Map<String, String> incomplete = new HashMap<>(breadth());
        incomplete.remove("down_issu_cnt");

        assertThatThrownBy(() -> KisDomesticMarketOverviewAdapter.toSegmentOverview(
                new KisDomesticMarketOverviewAdapter.SegmentDefinition("KOSPI", "0001", "K", "KSP"),
                incomplete, investor(), MARKET_DATE))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("down_issu_cnt");
    }

    private Map<String, String> breadth() {
        return Map.of(
                "bstp_nmix_prpr", "3200.50",
                "bstp_nmix_prdy_ctrt", "1.23",
                "acml_tr_pbmn", "15,000,000",
                "ascn_issu_cnt", "600",
                "down_issu_cnt", "300",
                "stnr_issu_cnt", "100",
                "uplm_issu_cnt", "2",
                "lslm_issu_cnt", "1"
        );
    }

    private Map<String, String> investor() {
        return Map.of(
                "stck_bsop_date", "20260720",
                "frgn_ntby_tr_pbmn", "120,000",
                "prsn_ntby_tr_pbmn", "-200,000",
                "orgn_ntby_tr_pbmn", "80,000"
        );
    }
}
