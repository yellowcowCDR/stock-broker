package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.application.port.out.LoadMarketWatchlistPort;
import com.hermes.broker.market.domain.MarketWatchlistResult;
import com.hermes.broker.market.domain.WatchlistCategory;
import com.hermes.broker.market.domain.WatchlistStock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MarketWatchlistServiceTest {

    @Mock
    private LoadMarketWatchlistPort loadMarketWatchlistPort;

    @Test
    void getWatchlist_returnsCompleteKisCandidates() {
        WatchlistStock candidate = new WatchlistStock(
                "005930", "삼성전자", "KRX", WatchlistCategory.MOMENTUM,
                new BigDecimal("100.00"), List.of("KIS 거래대금 순위 1")
        );
        MarketWatchlistResult source = new MarketWatchlistResult(
                List.of(candidate), "KIS_OPEN_API", Instant.parse("2026-07-18T01:00:00Z"),
                true, "FETCHED_NOW", true
        );
        given(loadMarketWatchlistPort.loadCandidates()).willReturn(source);

        MarketWatchlistResult result = new MarketWatchlistService(loadMarketWatchlistPort).getWatchlist();

        assertThat(result).isSameAs(source);
        assertThat(result.stocks()).containsExactly(candidate);
        assertThat(result.candidateOnly()).isTrue();
    }

    @Test
    void getWatchlist_rejectsIncompleteSourceInsteadOfFallingBack() {
        given(loadMarketWatchlistPort.loadCandidates()).willReturn(new MarketWatchlistResult(
                List.of(), "KIS_OPEN_API", Instant.parse("2026-07-18T01:00:00Z"),
                false, "UNKNOWN", true
        ));

        assertThatThrownBy(() -> new MarketWatchlistService(loadMarketWatchlistPort).getWatchlist())
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
