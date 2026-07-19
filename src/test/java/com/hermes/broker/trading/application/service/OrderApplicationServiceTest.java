package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import com.hermes.broker.market.application.service.StockSectorResolver;
import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.application.port.in.EvaluateOrderRiskUseCase;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.application.port.out.SubmitOrderPort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.domain.risk.RiskDecision;
import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    @Mock TradingLogRepository tradingLogRepository;
    @Mock MarketTradingPort marketTradingPort;
    @Mock SubmitOrderPort submitOrderPort;
    @Mock MarketTimeValidator marketTimeValidator;
    @Mock TradingEnvironmentGuard environmentGuard;
    @Mock OrderPriceValidator orderPriceValidator;
    @Mock DuplicateOrderValidator duplicateOrderValidator;
    @Mock EvaluateOrderRiskUseCase evaluateOrderRiskUseCase;
    @Mock OrderRequestHasher requestHasher;
    @Mock BrokerAccountKeyProvider accountKeyProvider;
    @Mock AccountLockService accountLockService;
    @Mock TradingTimeService tradingTimeService;
    @Mock StockSectorResolver stockSectorResolver;
    @Mock MarketContextGuard marketContextGuard;
    @Mock OrderDecisionLinkValidator orderDecisionLinkValidator;

    private OrderApplicationService service;
    private OrderRequestDto request;

    @BeforeEach
    void setUp() {
        RiskPolicyProperties policy = new RiskPolicyProperties(
                "risk-v1",
                new BigDecimal("0.03"),
                new BigDecimal("1000000"),
                5,
                5,
                new BigDecimal("0.40"),
                new BigDecimal("0.25"),
                new BigDecimal("0.10"),
                false,
                false,
                false,
                true,
                true
        );
        service = new OrderApplicationService(
                tradingLogRepository,
                List.of(marketTradingPort),
                List.of(submitOrderPort),
                marketTimeValidator,
                environmentGuard,
                orderPriceValidator,
                duplicateOrderValidator,
                evaluateOrderRiskUseCase,
                requestHasher,
                accountKeyProvider,
                accountLockService,
                policy,
                tradingTimeService,
                stockSectorResolver,
                marketContextGuard,
                orderDecisionLinkValidator
        );

        request = OrderRequestDto.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .orderType(OrderType.BUY)
                .price(new BigDecimal("70000"))
                .quantity(1)
                .idempotencyKey("20260718-DOMESTIC-005930-BUY-1")
                .decisionId("decision-1")
                .featureId("feature-1")
                .strategyVersion("strategy-v1")
                .build();

        when(accountKeyProvider.getAccountKey()).thenReturn("account-key");
        when(accountLockService.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
        when(requestHasher.hash(any())).thenReturn("request-hash");
        lenient().when(tradingLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        lenient().when(tradingLogRepository.findByDecisionId(anyString())).thenReturn(Optional.empty());
        lenient().when(orderDecisionLinkValidator.validate(any())).thenReturn(validatedDecision());
        lenient().when(tradingLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stockSectorResolver.resolve("005930", MarketType.DOMESTIC))
                .thenReturn(new StockSector(
                        "005930", MarketType.DOMESTIC, "013", "전기전자",
                        "INDEX_INDUSTRY_MEDIUM", "KIS_OPEN_API:SEARCH_STOCK_INFO",
                        Instant.parse("2026-07-19T00:00:00Z"), true));
        lenient().when(marketContextGuard.validateEntry(MarketType.DOMESTIC, "context-1"))
                .thenReturn(marketContext());
    }

    @Test
    void environmentRejectionNeverCallsKis() {
        doThrow(new IllegalStateException("Entry kill switch is active"))
                .when(environmentGuard).validateSubmission(request);

        OrderResponseDto response = service.placeOrder(request);

        assertFalse(response.isSuccess());
        assertEquals(OrderStatus.REJECTED, response.getStatus());
        verify(submitOrderPort, never()).placeOrder(any());
        verify(marketTradingPort, never()).getCurrentPrice(anyString());
    }

    @Test
    void closedMarketNeverCallsKisOrder() {
        doThrow(new IllegalStateException("Market is closed"))
                .when(marketTimeValidator).validateMarketOpen("DOMESTIC");

        OrderResponseDto response = service.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, response.getStatus());
        verify(submitOrderPort, never()).placeOrder(any());
    }

    @Test
    void riskRejectionNeverCallsKisOrder() {
        prepareUntilRiskEvaluation();
        when(evaluateOrderRiskUseCase.evaluate(any())).thenReturn(new RiskEvaluationResult(
                false,
                RiskDecision.BLOCKED_BY_MAX_ORDER_AMOUNT,
                List.of("Order amount exceeds max limit"),
                Map.of("orderAmount", new BigDecimal("70000"))
        ));

        OrderResponseDto response = service.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, response.getStatus());
        verify(submitOrderPort, never()).placeOrder(any());
    }

    @Test
    void duplicateOpenOrderNeverCallsKisOrder() {
        when(marketTradingPort.supports(MarketType.DOMESTIC)).thenReturn(true);
        when(marketTradingPort.getCurrentPrice("005930")).thenReturn(currentPrice());
        doThrow(new IllegalStateException("duplicate"))
                .when(duplicateOrderValidator).validate("account-key", request);

        OrderResponseDto response = service.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, response.getStatus());
        verify(submitOrderPort, never()).placeOrder(any());
    }

    @Test
    void missingRealSectorDataRejectsBuyBeforeRiskAndKisOrder() {
        prepareUntilRiskEvaluation();
        when(stockSectorResolver.resolve("005930", MarketType.DOMESTIC))
                .thenThrow(new com.hermes.broker.common.exception.MarketDataUnavailableException("unavailable"));

        OrderResponseDto response = service.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, response.getStatus());
        verifyNoInteractions(evaluateOrderRiskUseCase, submitOrderPort);
    }

    @Test
    void missingMarketContextRejectsBuyBeforeSectorRiskAndKisOrder() {
        prepareUntilRiskEvaluation();
        when(marketContextGuard.validateEntry(MarketType.DOMESTIC, "context-1"))
                .thenThrow(new IllegalStateException("No market context"));

        OrderResponseDto response = service.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, response.getStatus());
        verify(stockSectorResolver, never()).resolve(anyString(), any());
        verifyNoInteractions(evaluateOrderRiskUseCase, submitOrderPort);
    }

    @Test
    void sellDoesNotDependOnEntrySectorLookup() {
        request = OrderRequestDto.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .orderType(OrderType.SELL)
                .price(new BigDecimal("70000"))
                .quantity(1)
                .idempotencyKey("20260718-DOMESTIC-005930-SELL-1")
                .decisionId("decision-1")
                .featureId("feature-1")
                .strategyVersion("strategy-v1")
                .build();
        prepareUntilRiskEvaluation();
        when(evaluateOrderRiskUseCase.evaluate(any())).thenReturn(new RiskEvaluationResult(
                false, RiskDecision.BLOCKED_BY_INSUFFICIENT_POSITION,
                List.of("Insufficient sellable quantity"), Map.of()));

        OrderResponseDto response = service.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, response.getStatus());
        verify(stockSectorResolver, never()).resolve(anyString(), any());
        verify(marketContextGuard, never()).validateEntry(any(), anyString());
        verify(submitOrderPort, never()).placeOrder(any());
    }

    @Test
    void successfulOrderCallsKisExactlyOnce() {
        prepareUntilRiskEvaluation();
        when(evaluateOrderRiskUseCase.evaluate(any())).thenReturn(new RiskEvaluationResult(
                true, RiskDecision.ALLOWED, List.of(), Map.of()));
        when(submitOrderPort.supports(MarketType.DOMESTIC)).thenReturn(true);
        when(submitOrderPort.placeOrder(request)).thenReturn(OrderResponseDto.builder()
                .success(true)
                .orderId("001-12345")
                .message("accepted")
                .build());

        OrderResponseDto response = service.placeOrder(request);

        assertTrue(response.isSuccess());
        assertEquals(OrderStatus.SUBMITTED, response.getStatus());
        assertEquals("001-12345", response.getOrderId());
        verify(submitOrderPort, times(1)).placeOrder(request);
        ArgumentCaptor<com.hermes.broker.trading.application.port.in.OrderRiskCommand> riskCommand =
                ArgumentCaptor.forClass(com.hermes.broker.trading.application.port.in.OrderRiskCommand.class);
        verify(evaluateOrderRiskUseCase).evaluate(riskCommand.capture());
        assertEquals("전기전자", riskCommand.getValue().sector());
        assertEquals("context-1", riskCommand.getValue().marketContextId());
        assertEquals(new BigDecimal("0.5"), riskCommand.getValue().marketRiskMultiplier());
        ArgumentCaptor<TradingLog> savedOrder = ArgumentCaptor.forClass(TradingLog.class);
        verify(tradingLogRepository, atLeast(3)).save(savedOrder.capture());
        assertEquals("context-1", savedOrder.getAllValues().get(savedOrder.getAllValues().size() - 1)
                .getMarketContextId());
        assertEquals("decision-1", savedOrder.getValue().getDecisionId());
        assertEquals("feature-1", savedOrder.getValue().getFeatureId());
        assertEquals("strategy-v1", savedOrder.getValue().getStrategyVersion());
    }

    @Test
    void overseasOrderUsesExchangeSpecificQuoteRiskAndKisSubmission() {
        request = OrderRequestDto.builder()
                .marketType(MarketType.OVERSEAS)
                .stockCode("AAPL")
                .exchangeCode("NASDAQ")
                .orderType(OrderType.BUY)
                .price(new BigDecimal("195.50"))
                .quantity(1)
                .idempotencyKey("20260719-OVERSEAS-AAPL-BUY-1")
                .decisionId("decision-1")
                .featureId("feature-1")
                .strategyVersion("strategy-v1")
                .build();
        when(marketTradingPort.supports(MarketType.OVERSEAS)).thenReturn(true);
        when(marketTradingPort.getCurrentPrice("AAPL", "NASD")).thenReturn(CurrentPriceDto.builder()
                .stockCode("AAPL").currentPrice(new BigDecimal("195.50")).build());
        when(marketContextGuard.validateEntry(MarketType.OVERSEAS, "context-1"))
                .thenReturn(marketContext(MarketType.OVERSEAS, "context-us"));
        when(stockSectorResolver.resolve("AAPL", MarketType.OVERSEAS))
                .thenReturn(new StockSector(
                        "AAPL", MarketType.OVERSEAS, "TECHNOLOGY", "Technology",
                        "GICS_SECTOR_PROVIDER_VALUE", "ALPHA_VANTAGE:OVERVIEW:Sector",
                        Instant.parse("2026-07-19T00:00:00Z"), true));
        when(evaluateOrderRiskUseCase.evaluate(any())).thenReturn(new RiskEvaluationResult(
                true, RiskDecision.ALLOWED, List.of(), Map.of()));
        when(submitOrderPort.supports(MarketType.OVERSEAS)).thenReturn(true);
        when(submitOrderPort.placeOrder(request)).thenReturn(OrderResponseDto.builder()
                .success(true).orderId("NASD-12345").message("accepted").build());

        OrderResponseDto response = service.placeOrder(request);

        assertTrue(response.isSuccess());
        assertEquals("NASD-12345", response.getOrderId());
        verify(marketTradingPort).getCurrentPrice("AAPL", "NASD");
        ArgumentCaptor<com.hermes.broker.trading.application.port.in.OrderRiskCommand> riskCommand =
                ArgumentCaptor.forClass(com.hermes.broker.trading.application.port.in.OrderRiskCommand.class);
        verify(evaluateOrderRiskUseCase).evaluate(riskCommand.capture());
        assertEquals("NASD", riskCommand.getValue().exchangeCode());
        ArgumentCaptor<TradingLog> saved = ArgumentCaptor.forClass(TradingLog.class);
        verify(tradingLogRepository, atLeastOnce()).save(saved.capture());
        assertEquals("NASD", saved.getValue().getExchangeCode());
    }

    @Test
    void repeatedIdempotencyKeyReturnsStoredResultWithoutKisCall() {
        TradingLog stored = TradingLog.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .stockName("Samsung")
                .accountKey("account-key")
                .idempotencyKey(request.getIdempotencyKey())
                .requestHash("request-hash")
                .orderType(OrderType.BUY)
                .orderPrice(request.getPrice())
                .orderQuantity(1)
                .status(OrderStatus.VALIDATING)
                .riskPolicyVersion("risk-v1")
                .build();
        stored.markSubmitted("001-12345", "accepted");
        when(tradingLogRepository.findByIdempotencyKey(request.getIdempotencyKey()))
                .thenReturn(Optional.of(stored));

        OrderResponseDto response = service.placeOrder(request);

        assertTrue(response.isSuccess());
        assertTrue(response.isReplayed());
        verifyNoInteractions(marketTradingPort, submitOrderPort);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadIsRejected() {
        TradingLog stored = TradingLog.builder()
                .marketType(MarketType.DOMESTIC)
                .stockCode("005930")
                .stockName("Samsung")
                .accountKey("account-key")
                .idempotencyKey(request.getIdempotencyKey())
                .requestHash("another-request-hash")
                .orderType(OrderType.BUY)
                .orderPrice(request.getPrice())
                .orderQuantity(1)
                .status(OrderStatus.SUBMITTED)
                .riskPolicyVersion("risk-v1")
                .build();
        when(tradingLogRepository.findByIdempotencyKey(request.getIdempotencyKey()))
                .thenReturn(Optional.of(stored));

        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(request));
        verifyNoInteractions(marketTradingPort, submitOrderPort);
    }

    private void prepareUntilRiskEvaluation() {
        when(marketTradingPort.supports(MarketType.DOMESTIC)).thenReturn(true);
        when(marketTradingPort.getCurrentPrice("005930")).thenReturn(currentPrice());
    }

    private CurrentPriceDto currentPrice() {
        return CurrentPriceDto.builder()
                .stockCode("005930")
                .currentPrice(new BigDecimal("70000"))
                .build();
    }

    private MarketContext marketContext() {
        return marketContext(MarketType.DOMESTIC, "context-1");
    }

    private MarketContext marketContext(MarketType marketType, String contextId) {
        Instant fetchedAt = Instant.parse("2026-07-19T00:00:00Z");
        MarketOverview overview = new MarketOverview(
                marketType, List.of(), 1, 0, 0, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "KIS_API_NATIVE", "KIS_OPEN_API:TEST", fetchedAt,
                fetchedAt.plusSeconds(300), true, "FRESH");
        return new MarketContext(
                contextId, marketType, MarketEntryPolicy.REDUCE_NEW_ENTRIES,
                new BigDecimal("0.5"), overview, List.of("test"), "hermes",
                "correlation-1", fetchedAt, fetchedAt.plusSeconds(300));
    }

    private ValidatedOrderDecision validatedDecision() {
        TradingFeatureSnapshot feature = new TradingFeatureSnapshot(
                "feature-1", "005930", MarketType.DOMESTIC,
                Map.of(), Map.of(), Map.of("marketContextId", "context-1"),
                Instant.parse("2026-07-19T00:00:00Z"));
        TradingDecision decision = new TradingDecision(
                "decision-1", "feature-1", "005930", TradingDecisionType.BUY,
                "strategy-v1", "test", new BigDecimal("70000"), BigDecimal.ONE,
                Instant.parse("2026-07-19T00:01:00Z"));
        return new ValidatedOrderDecision(decision, feature);
    }
}
