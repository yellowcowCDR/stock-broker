package com.hermes.broker.market.adapter.out.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KisOverseasTradingAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KisProperties kisProperties = mock(KisProperties.class);
    private final KisOverseasTradingAdapter adapter = new KisOverseasTradingAdapter(
            mock(RestClient.Builder.class),
            mock(KisHeaderProvider.class),
            kisProperties,
            mock(TradingProperties.class),
            mock(KisRestClientInterceptor.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void parsesUsdCashAndRealSellableQuantity() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output1": [{
                    "pdno": "AAPL",
                    "ovrs_excg_cd": "NASD",
                    "buy_crcy_cd": "USD",
                    "cblc_qty13": "10",
                    "ord_psbl_qty1": "7",
                    "avg_unpr3": "180.25",
                    "ovrs_now_pric1": "195.50",
                    "frcr_evlu_amt2": "1955.00",
                    "evlu_pfls_amt2": "152.50",
                    "evlu_pfls_rt1": "8.46"
                  }],
                  "output2": [{
                    "crcy_cd": "USD",
                    "frcr_dncl_amt_2": "2500.75"
                  }],
                  "output3": {
                    "frcr_use_psbl_amt": "2300.50"
                  }
                }
                """);

        OverseasAccountSnapshot snapshot = adapter.parseUnitedStatesAccount(response, NOW);

        assertThat(snapshot.cashBalance()).isEqualByComparingTo("2500.75");
        assertThat(snapshot.availableForUse()).isEqualByComparingTo("2300.50");
        assertThat(snapshot.positions()).hasSize(1);
        assertThat(snapshot.positions().get(0).stockCode()).isEqualTo("AAPL");
        assertThat(snapshot.positions().get(0).sellableQuantity()).isEqualByComparingTo("7");
        assertThat(snapshot.positions().get(0).profitLossRate()).isEqualByComparingTo("0.0846");
        assertThat(snapshot.complete()).isTrue();
    }

    @Test
    void parsesSymbolExchangeAndPriceSpecificUsdCapacity() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output": {
                    "tr_crcy_cd": "USD",
                    "ord_psbl_frcr_amt": "2300.50",
                    "ovrs_ord_psbl_amt": "3000000",
                    "max_ord_psbl_qty": "11",
                    "ord_psbl_qty": "11",
                    "exrt": "1380.25"
                  }
                }
                """);

        OverseasOrderCapacity capacity = adapter.parseOrderCapacity(
                response, "AAPL", "NASD", new BigDecimal("195.50"), NOW);

        assertThat(capacity.orderableForeignAmount()).isEqualByComparingTo("2300.50");
        assertThat(capacity.maximumOrderableQuantity()).isEqualByComparingTo("11");
        assertThat(capacity.requestedPrice()).isEqualByComparingTo("195.50");
        assertThat(capacity.currency()).isEqualTo("USD");
        assertThat(capacity.complete()).isTrue();
    }

    @Test
    void missingSellableQuantityFailsClosed() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output1": [{
                    "pdno": "AAPL",
                    "ovrs_excg_cd": "NASD",
                    "buy_crcy_cd": "USD",
                    "cblc_qty13": "10",
                    "avg_unpr3": "180.25",
                    "ovrs_now_pric1": "195.50",
                    "frcr_evlu_amt2": "1955.00",
                    "evlu_pfls_amt2": "152.50",
                    "evlu_pfls_rt1": "8.46"
                  }],
                  "output2": [{"crcy_cd": "USD", "frcr_dncl_amt_2": "2500.75"}],
                  "output3": {"frcr_use_psbl_amt": "2300.50"}
                }
                """);

        assertThatThrownBy(() -> adapter.parseUnitedStatesAccount(response, NOW))
                .isInstanceOf(DataPipelineUnavailableException.class)
                .hasMessageContaining("sellable quantity");
    }

    @Test
    void usesOfficialKisPaperOrderTransactionIds() {
        when(kisProperties.environment()).thenReturn(KisEnvironment.MOCK);

        assertThat(adapter.overseasOrderTrId(com.hermes.broker.trading.domain.OrderType.BUY))
                .isEqualTo("VTTT1002U");
        assertThat(adapter.overseasOrderTrId(com.hermes.broker.trading.domain.OrderType.SELL))
                .isEqualTo("VTTT1001U");
    }

    @Test
    void parsesOverseasOpenOrderWithoutInventingQuantityOrSide() throws Exception {
        JsonNode row = objectMapper.readTree("""
                {
                  "odno": "12345",
                  "pdno": "AAPL",
                  "ovrs_excg_cd": "NASD",
                  "sll_buy_dvsn_cd": "02",
                  "ft_ord_qty": "3",
                  "ft_ccld_qty": "1",
                  "ft_ord_unpr3": "195.50",
                  "ord_dt": "20260719",
                  "ord_tmd": "101530"
                }
                """);

        var order = adapter.parseOpenOrder(
                row, com.hermes.broker.trading.domain.OverseasExchange.NASD);

        assertThat(order.orderId()).isEqualTo("NASD-12345");
        assertThat(order.exchangeCode()).isEqualTo("NASD");
        assertThat(order.orderType()).isEqualTo(com.hermes.broker.trading.domain.OrderType.BUY);
        assertThat(order.quantity()).isEqualByComparingTo("3");
        assertThat(order.executedQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void parsesOverseasExecutionForReconciliation() throws Exception {
        JsonNode row = objectMapper.readTree("""
                {
                  "odno": "12345",
                  "orgn_odno": "",
                  "pdno": "AAPL",
                  "ovrs_excg_cd": "NASD",
                  "sll_buy_dvsn_cd": "02",
                  "ft_ord_qty": "3",
                  "ft_ccld_qty": "3",
                  "ft_ccld_unpr3": "195.25",
                  "nccs_qty": "0",
                  "rvse_cncl_dvsn": "00",
                  "rjct_rson": "0",
                  "prcs_stat_name": "체결완료"
                }
                """);

        var execution = adapter.parseOrderExecution(row);

        assertThat(execution.orderId()).isEqualTo("12345");
        assertThat(execution.executedQuantity()).isEqualByComparingTo("3");
        assertThat(execution.executionPrice()).isEqualByComparingTo("195.25");
        assertThat(execution.rejected()).isFalse();
        assertThat(execution.canceled()).isFalse();
    }
}
