package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.market.dto.response.MarketStatusResponseDto;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionCommand;
import com.hermes.broker.trading.application.port.in.CreateTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.ShadowDecisionResult;
import com.hermes.broker.trading.application.port.in.StartShadowDecisionCommand;
import com.hermes.broker.trading.application.port.out.LoadShadowPerformanceSamplePort;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.application.port.out.SaveShadowPerformanceSamplePort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShadowTradingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T07:00:00Z");

    @Mock CreateTradingDecisionUseCase decisionUseCase;
    @Mock LoadTradingDecisionPort decisionPort;
    @Mock LoadTradingFeaturePort featurePort;
    @Mock LoadShadowPerformanceSamplePort sampleLoadPort;
    @Mock SaveShadowPerformanceSamplePort sampleSavePort;
    @Mock MarketTradingPort marketPort;
    @Mock MarketTimeValidator marketTimeValidator;

    private ShadowTradingService service;
    private TradingFeatureSnapshot feature;
    private TradingDecision decision;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ShadowTradingService(
                decisionUseCase, decisionPort, featurePort, sampleLoadPort,
                sampleSavePort, List.of(marketPort), marketTimeValidator,
                new TradingTimeService(clock), clock);
        feature = new TradingFeatureSnapshot(
                "feature-1", "005930", MarketType.DOMESTIC,
                Map.of(), Map.of(), Map.of("marketContextId", "context-1"),
                NOW.minusSeconds(60), "feature-key");
        decision = new TradingDecision(
                "decision-1", "feature-1", "005930", TradingDecisionType.BUY,
                "2", "shadow buy", new BigDecimal("100"), BigDecimal.ONE,
                NOW.minusSeconds(30), TradingDecisionMode.SHADOW, "shadow-key");
    }

    @Test
    void startsPendingSampleWithBrokerFetchedQuoteAndNoOrderPath() {
        CreateTradingDecisionCommand command = new CreateTradingDecisionCommand(
                "feature-1", TradingDecisionType.BUY, 2, "shadow buy",
                new BigDecimal("100"), BigDecimal.ONE, "shadow-key");
        when(decisionPort.loadByIdempotencyKey("shadow-key")).thenReturn(Optional.empty());
        when(decisionUseCase.createShadowDecision(command)).thenReturn(decision);
        when(sampleLoadPort.loadByDecisionId("decision-1")).thenReturn(Optional.empty());
        when(featurePort.loadById("feature-1")).thenReturn(Optional.of(feature));
        when(marketPort.supports(MarketType.DOMESTIC)).thenReturn(true);
        when(marketPort.getCurrentPrice("005930")).thenReturn(CurrentPriceDto.builder()
                .stockCode("005930").currentPrice(new BigDecimal("101")).build());
        when(sampleSavePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShadowDecisionResult result = service.start(new StartShadowDecisionCommand(command, null));

        assertThat(result.replayed()).isFalse();
        assertThat(result.sample().referencePrice()).isEqualByComparingTo("101");
        assertThat(result.sample().status()).isEqualTo(ShadowSampleStatus.PENDING);
        verify(marketTimeValidator).validateMarketOpen("DOMESTIC");
    }

    @Test
    void settlesAfterMarketCloseUsingSecondBrokerQuote() {
        LocalDate tradingDate = LocalDate.of(2026, 7, 20);
        ShadowPerformanceSample pending = new ShadowPerformanceSample(
                "sample-1", "decision-1", "feature-1", "2", "005930",
                MarketType.DOMESTIC, null, TradingDecisionType.BUY,
                new BigDecimal("100"), null, null, null, tradingDate,
                ShadowSampleStatus.PENDING, "KIS_QUOTE_API", NOW.minusSeconds(7200), null);
        when(marketTimeValidator.getMarketStatus("DOMESTIC")).thenReturn(
                MarketStatusResponseDto.builder()
                        .marketType("DOMESTIC").complete(true).isOpen(false)
                        .sessionClosesAt(NOW.minusSeconds(1800)).checkedAt(NOW).build());
        when(sampleLoadPort.loadByMarketAndTradingDateAndStatus(
                MarketType.DOMESTIC, tradingDate, ShadowSampleStatus.PENDING))
                .thenReturn(List.of(pending));
        when(featurePort.loadById("feature-1")).thenReturn(Optional.of(feature));
        when(marketPort.supports(MarketType.DOMESTIC)).thenReturn(true);
        when(marketPort.getCurrentPrice("005930")).thenReturn(CurrentPriceDto.builder()
                .stockCode("005930").currentPrice(new BigDecimal("103")).build());
        when(sampleSavePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ShadowPerformanceSample> result = service.settle(MarketType.DOMESTIC, tradingDate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ShadowSampleStatus.COMPLETED);
        assertThat(result.get(0).rawReturnRate()).isEqualByComparingTo("3.000000");
        assertThat(result.get(0).actionReturnRate()).isEqualByComparingTo("3.000000");
    }
}
