package com.hermes.broker.market.adapter.out.external;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KisDomesticTradingAdapterTest {

    @Test
    void convertsKisPercentageToDecimalRiskRate() {
        KisDomesticTradingAdapter.DailyAssetChangeData result =
                KisDomesticTradingAdapter.toDailyAssetChangeData(Map.of(
                        "bfdy_tot_asst_evlu_amt", "1,010,000",
                        "asst_icdc_amt", "-10,000",
                        "asst_icdc_erng_rt", "-0.99"
                ));

        assertThat(result.previousTotalAssetAmount()).isEqualByComparingTo("1010000");
        assertThat(result.amount()).isEqualByComparingTo("-10000");
        assertThat(result.rate()).isEqualByComparingTo("-0.0099");
        assertThat(result.complete()).isTrue();
        assertThat(result.dataSource()).isEqualTo("KIS_OPEN_API:INQUIRE_BALANCE:ASST_ICDC");
    }

    @Test
    void missingKisDailyFieldIsExplicitlyIncomplete() {
        KisDomesticTradingAdapter.DailyAssetChangeData result =
                KisDomesticTradingAdapter.toDailyAssetChangeData(Map.of(
                        "bfdy_tot_asst_evlu_amt", "1010000",
                        "asst_icdc_amt", "-10000"
                ));

        assertThat(result.rate()).isNull();
        assertThat(result.complete()).isFalse();
        assertThat(result.dataSource()).isNull();
    }
}
