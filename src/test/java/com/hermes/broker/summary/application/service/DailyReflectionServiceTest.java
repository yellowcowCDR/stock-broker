package com.hermes.broker.summary.application.service;

import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.market.domain.MarketSegmentOverview;
import com.hermes.broker.summary.application.port.out.DailySummaryRepository;
import com.hermes.broker.summary.application.port.out.LoadTradingReflectionPort;
import com.hermes.broker.summary.application.port.out.SaveTradingReflectionPort;
import com.hermes.broker.summary.domain.DailySummary;
import com.hermes.broker.summary.domain.TradingReflection;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DailyReflectionServiceTest {

    @Mock LoadTradingDecisionPort decisionPort;
    @Mock LoadTradingFeaturePort featurePort;
    @Mock TradingLogRepository tradingLogRepository;
    @Mock LoadMarketContextPort marketContextPort;
    @Mock DailySummaryRepository dailySummaryRepository;
    @Mock LoadTradingReflectionPort reflectionPort;
    @Mock SaveTradingReflectionPort saveReflectionPort;

    private DailyReflectionService service;
    private TradingDecision decision;
    private TradingFeatureSnapshot feature;
    private MarketContext context;
    private final LocalDate tradingDate = LocalDate.of(2026, 7, 19);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-19T08:00:00Z"), ZoneOffset.UTC);
        service = new DailyReflectionService(
                new TradingTimeService(clock), decisionPort, featurePort, tradingLogRepository,
                marketContextPort, dailySummaryRepository, reflectionPort, saveReflectionPort);

        Instant snapshotAt = Instant.parse("2026-07-19T00:00:00Z");
        Instant decidedAt = Instant.parse("2026-07-19T00:01:00Z");
        feature = new TradingFeatureSnapshot(
                "feature-1", "005930", MarketType.DOMESTIC,
                Map.of("rsi", "45"), Map.of(), Map.of("marketContextId", "context-1"), snapshotAt);
        decision = new TradingDecision(
                "decision-1", "feature-1", "005930", TradingDecisionType.HOLD,
                "strategy-v1", "No entry", null, BigDecimal.ZERO, decidedAt);
        context = context(snapshotAt, decidedAt.plusSeconds(600));

        when(featurePort.loadById("feature-1")).thenReturn(Optional.of(feature));
        when(decisionPort.loadBetween(any(), any())).thenReturn(List.of(decision));
        when(tradingLogRepository.findAllByCreatedAtRange(any(), any())).thenReturn(List.of());
        lenient().when(marketContextPort.loadById("context-1")).thenReturn(Optional.of(context));
        when(dailySummaryRepository.findByMarketTypeAndTradeDate(MarketType.DOMESTIC, tradingDate))
                .thenReturn(Optional.of(summary()));
        lenient().when(reflectionPort.loadByIdentity(tradingDate, MarketType.DOMESTIC, "strategy-v1"))
                .thenReturn(Optional.empty());
    }

    @Test
    void persistsHoldDecisionWithThenTimeFeatureAndMarketContext() {
        List<TradingReflection> result = service.runDailyReflection(MarketType.DOMESTIC, tradingDate);

        assertThat(result).hasSize(1);
        TradingReflection reflection = result.get(0);
        assertThat(reflection.marketZoneId()).isEqualTo("Asia/Seoul");
        assertThat(reflection.holdCount()).isEqualTo(1);
        assertThat(reflection.blockCount()).isZero();
        assertThat(reflection.dataComplete()).isTrue();
        assertThat(reflection.reviews().get(0).featureId()).isEqualTo("feature-1");
        assertThat(reflection.reviews().get(0).marketContextId()).isEqualTo("context-1");

        ArgumentCaptor<TradingReflection> saved = ArgumentCaptor.forClass(TradingReflection.class);
        verify(saveReflectionPort).save(saved.capture());
        assertThat(saved.getValue().dailyReturnRate()).isEqualByComparingTo("0.25");
    }

    @Test
    void blocksExecutedOrderUntilKisCostsAreReconciled() {
        decision = new TradingDecision(
                "decision-1", "feature-1", "005930", TradingDecisionType.BUY,
                "strategy-v1", "Entry", new BigDecimal("70000"), BigDecimal.ONE,
                Instant.parse("2026-07-19T00:01:00Z"));
        when(decisionPort.loadBetween(any(), any())).thenReturn(List.of(decision));
        TradingLog order = TradingLog.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .stockName("Samsung")
                .orderType(OrderType.BUY)
                .orderPrice(new BigDecimal("70000"))
                .orderQuantity(1)
                .decisionId("decision-1")
                .featureId("feature-1")
                .strategyVersion("strategy-v1")
                .status(OrderStatus.EXECUTED)
                .build();
        order.linkMarketContext("context-1");
        order.updateExecution(new BigDecimal("70100"), BigDecimal.ONE);
        when(tradingLogRepository.findAllByCreatedAtRange(any(), any())).thenReturn(List.of(order));

        assertThatThrownBy(() -> service.runDailyReflection(MarketType.DOMESTIC, tradingDate))
                .isInstanceOf(DataPipelineUnavailableException.class)
                .hasMessageContaining("incomplete KIS cost/slippage reconciliation")
                .hasMessageContaining("Synthetic fallback is disabled");
    }

    @Test
    void excludesShadowDecisionsFromActivePortfolioReflection() {
        TradingDecision shadow = new TradingDecision(
                "shadow-decision", "shadow-feature", "005930", TradingDecisionType.BUY,
                "2", "counterfactual", new BigDecimal("70000"), BigDecimal.ONE,
                Instant.parse("2026-07-19T00:02:00Z"),
                TradingDecisionMode.SHADOW, "shadow-key");
        when(decisionPort.loadBetween(any(), any())).thenReturn(List.of(decision, shadow));

        TradingReflection result = service.runDailyReflection(
                MarketType.DOMESTIC, tradingDate).get(0);

        assertThat(result.decisionCount()).isEqualTo(1);
        assertThat(result.strategyVersion()).isEqualTo("strategy-v1");
    }

    private DailySummary summary() {
        return DailySummary.builder()
                .tradeDate(tradingDate)
                .marketType(MarketType.DOMESTIC)
                .closingTotalAsset(new BigDecimal("1002500"))
                .dailyReturnRate(new BigDecimal("0.25"))
                .totalTradeCount(0)
                .retrospectiveReport("real summary")
                .build();
    }

    private MarketContext context(Instant analyzedAt, Instant validUntil) {
        MarketSegmentOverview segment = new MarketSegmentOverview(
                "KOSPI", "0001", new BigDecimal("3200"), new BigDecimal("0.5"),
                new BigDecimal("1000000"), 500, 300, 10, 2, 1,
                new BigDecimal("0.2"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "KRW", tradingDate);
        MarketOverview overview = new MarketOverview(
                MarketType.DOMESTIC, List.of(segment), 500, 300, 10, new BigDecimal("0.2"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "KRW",
                "KIS_OPEN_API", analyzedAt, validUntil, true, "FRESH");
        return new MarketContext(
                "context-1", MarketType.DOMESTIC, MarketEntryPolicy.ALLOW_NEW_ENTRIES,
                BigDecimal.ONE, overview, List.of("test"), "hermes", "correlation-1",
                analyzedAt, validUntil);
    }
}
