package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import com.hermes.broker.market.application.service.StockSectorResolver;
import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.application.port.in.AgentTradingUseCase;
import com.hermes.broker.trading.application.port.in.EvaluateOrderRiskUseCase;
import com.hermes.broker.trading.application.port.in.OrderRiskCommand;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.application.port.out.SubmitOrderPort;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OverseasExchange;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService implements AgentTradingUseCase {

    private final TradingLogRepository tradingLogRepository;
    private final List<MarketTradingPort> marketTradingPorts;
    private final List<SubmitOrderPort> submitOrderPorts;
    private final MarketTimeValidator marketTimeValidator;
    private final TradingEnvironmentGuard environmentGuard;
    private final OrderPriceValidator orderPriceValidator;
    private final DuplicateOrderValidator duplicateOrderValidator;
    private final EvaluateOrderRiskUseCase evaluateOrderRiskUseCase;
    private final OrderRequestHasher requestHasher;
    private final BrokerAccountKeyProvider accountKeyProvider;
    private final AccountLockService accountLockService;
    private final RiskPolicyProperties riskPolicyProperties;
    private final TradingTimeService tradingTimeService;
    private final StockSectorResolver stockSectorResolver;
    private final MarketContextGuard marketContextGuard;
    private final OrderDecisionLinkValidator orderDecisionLinkValidator;

    @Override
    public OrderResponseDto placeOrder(OrderRequestDto request) {
        validateRequiredFields(request);
        String accountKey = accountKeyProvider.getAccountKey();
        return accountLockService.executeWithLock(accountKey, () -> placeOrderLocked(accountKey, request));
    }

    private OrderResponseDto placeOrderLocked(String accountKey, OrderRequestDto request) {
        String requestHash = requestHasher.hash(request);
        var existing = tradingLogRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            assertSameRequest(existing.get(), requestHash);
            return toResponse(existing.get(), true);
        }

        ValidatedOrderDecision decisionLink = orderDecisionLinkValidator.validate(request);
        tradingLogRepository.findByDecisionId(decisionLink.decision().decisionId())
                .ifPresent(previous -> {
                    throw new IllegalStateException("Decision " + decisionLink.decision().decisionId()
                            + " already has Broker order " + previous.getId()
                            + "; retry with the original idempotency key.");
                });

        TradingLog order = createOrder(accountKey, request, requestHash, decisionLink);
        boolean submissionAttempted = false;
        try {
            order = tradingLogRepository.save(order);
        } catch (DataIntegrityViolationException concurrentInsert) {
            TradingLog concurrentOrder = tradingLogRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> concurrentInsert);
            assertSameRequest(concurrentOrder, requestHash);
            return toResponse(concurrentOrder, true);
        }

        try {
            environmentGuard.validateSubmission(request);
            marketTimeValidator.validateMarketOpen(request.getMarketType().name());

            MarketTradingPort marketAdapter = findMarketAdapter(request);
            CurrentPriceDto latestPrice = request.getMarketType() == MarketType.OVERSEAS
                    ? marketAdapter.getCurrentPrice(request.getStockCode(), normalizedExchangeCode(request))
                    : marketAdapter.getCurrentPrice(request.getStockCode());
            orderPriceValidator.validate(request.getPrice(), latestPrice.getCurrentPrice());
            duplicateOrderValidator.validate(accountKey, request);

            MarketContext marketContext = resolveEntryContext(request, decisionLink);
            if (marketContext != null) {
                order.linkMarketContext(marketContext.contextId());
            }
            StockSector sectorMetadata = resolveEntrySector(request);
            String sector = sectorMetadata == null
                    ? "NOT_REQUIRED_FOR_SELL" : sectorMetadata.sectorName();

            OrderRiskCommand riskCommand = new OrderRiskCommand(
                    accountKey,
                    request.getStockCode(),
                    normalizedExchangeCode(request),
                    request.getMarketType(),
                    request.getOrderType(),
                    request.getPrice(),
                    java.math.BigDecimal.valueOf(request.getQuantity()),
                    sector,
                    marketContext == null ? null : marketContext.contextId(),
                    marketContext == null ? java.math.BigDecimal.ONE : marketContext.riskMultiplier()
            );
            RiskEvaluationResult riskResult = evaluateOrderRiskUseCase.evaluate(riskCommand);
            Map<String, Object> auditSnapshot = new HashMap<>();
            if (request.getSnapshotIndicators() != null) {
                auditSnapshot.putAll(request.getSnapshotIndicators());
            }
            auditSnapshot.put("latestPrice", latestPrice.getCurrentPrice());
            if (request.getMarketType() == MarketType.OVERSEAS) {
                auditSnapshot.put("exchangeCode", normalizedExchangeCode(request));
                auditSnapshot.put("currency", "USD");
            }
            auditSnapshot.put("sector", sector);
            if (marketContext != null) {
                auditSnapshot.put("marketContextId", marketContext.contextId());
                auditSnapshot.put("marketEntryPolicy", marketContext.entryPolicy().name());
                auditSnapshot.put("marketRiskMultiplier", marketContext.riskMultiplier());
                auditSnapshot.put("marketContextValidUntil", marketContext.validUntil());
                auditSnapshot.put("marketOverviewFetchedAt", marketContext.overviewSnapshot().fetchedAt());
                auditSnapshot.put("marketOverviewDataSource", marketContext.overviewSnapshot().dataSource());
            }
            if (sectorMetadata != null) {
                auditSnapshot.put("sectorCode", sectorMetadata.sectorCode());
                auditSnapshot.put("sectorClassificationLevel", sectorMetadata.classificationLevel());
                auditSnapshot.put("sectorDataSource", sectorMetadata.dataSource());
                auditSnapshot.put("sectorFetchedAt", sectorMetadata.fetchedAt());
            }
            auditSnapshot.put("riskDecision", riskResult.decision().name());
            auditSnapshot.put("riskReasons", riskResult.reasons());
            auditSnapshot.put("riskSnapshot", riskResult.snapshot());
            order.updateRiskSnapshot(auditSnapshot);

            if (!riskResult.allowed()) {
                order.markRejected(String.join(", ", riskResult.reasons()));
                tradingLogRepository.save(order);
                return toResponse(order, false);
            }

            SubmitOrderPort orderPort = findOrderPort(request);
            order.markSubmitting();
            tradingLogRepository.save(order);
            submissionAttempted = true;
            OrderResponseDto kisResponse = orderPort.placeOrder(request);
            if (kisResponse.isSuccess()) {
                order.markSubmitted(kisResponse.getOrderId(), kisResponse.getMessage());
            } else {
                order.markFailed(kisResponse.getMessage());
            }
            tradingLogRepository.save(order);
            return toResponse(order, false);
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            order.markRejected(rejected.getMessage());
            tradingLogRepository.save(order);
            return toResponse(order, false);
        } catch (Exception failure) {
            if (!submissionAttempted) {
                log.warn("Order precondition could not be satisfied. idempotencyKey={}, reason={}",
                        request.getIdempotencyKey(), failure.getMessage());
                order.markRejected("Order precondition could not be satisfied: " + failure.getMessage());
                tradingLogRepository.save(order);
                return toResponse(order, false);
            }

            log.error("Order result is ambiguous. idempotencyKey={}", request.getIdempotencyKey(), failure);
            order.markUnknown("Order result is ambiguous and requires reconciliation: " + failure.getMessage());
            tradingLogRepository.save(order);
            return toResponse(order, false);
        }
    }

    private MarketTradingPort findMarketAdapter(OrderRequestDto request) {
        return marketTradingPorts.stream()
                .filter(port -> port.supports(request.getMarketType()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported market type: " + request.getMarketType()));
    }

    private SubmitOrderPort findOrderPort(OrderRequestDto request) {
        return submitOrderPorts.stream()
                .filter(port -> port.supports(request.getMarketType()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Order submission is not supported for: " + request.getMarketType()));
    }

    private StockSector resolveEntrySector(OrderRequestDto request) {
        if (request.getOrderType() == OrderType.SELL) {
            return null;
        }
        return stockSectorResolver.resolve(
                request.getStockCode().trim().toUpperCase(), request.getMarketType());
    }

    private MarketContext resolveEntryContext(OrderRequestDto request,
                                              ValidatedOrderDecision decisionLink) {
        if (request.getOrderType() == OrderType.SELL) {
            return null;
        }
        Object contextId = decisionLink.feature().riskFeatures() == null
                ? null : decisionLink.feature().riskFeatures().get("marketContextId");
        if (contextId == null || contextId.toString().isBlank()) {
            throw new IllegalStateException("Decision Feature has no marketContextId.");
        }
        return marketContextGuard.validateEntry(
                request.getMarketType(), contextId.toString());
    }

    private TradingLog createOrder(String accountKey, OrderRequestDto request, String requestHash,
                                   ValidatedOrderDecision decisionLink) {
        return TradingLog.builder()
                .marketType(request.getMarketType())
                .stockCode(request.getStockCode().trim().toUpperCase())
                .exchangeCode(normalizedExchangeCode(request))
                .stockName("UNKNOWN")
                .accountKey(accountKey)
                .idempotencyKey(request.getIdempotencyKey())
                .requestHash(requestHash)
                .orderType(request.getOrderType())
                .orderPrice(request.getPrice())
                .orderQuantity(request.getQuantity())
                .status(OrderStatus.VALIDATING)
                .decisionReason(decisionLink.decision().reason())
                .snapshotIndicators(request.getSnapshotIndicators())
                .riskPolicyVersion(riskPolicyProperties.version())
                .decisionId(decisionLink.decision().decisionId())
                .featureId(decisionLink.feature().featureId())
                .strategyVersion(decisionLink.decision().strategyVersion())
                .build();
    }

    private void assertSameRequest(TradingLog existing, String requestHash) {
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new IllegalArgumentException("Idempotency key was already used with a different order payload.");
        }
    }

    private void validateRequiredFields(OrderRequestDto request) {
        if (request == null || request.getMarketType() == null || request.getOrderType() == null
                || request.getStockCode() == null || request.getStockCode().isBlank()
                || request.getPrice() == null || request.getPrice().signum() <= 0
                || request.getQuantity() <= 0
                || request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
                || request.getDecisionId() == null || request.getDecisionId().isBlank()
                || request.getFeatureId() == null || request.getFeatureId().isBlank()
                || request.getStrategyVersion() == null || request.getStrategyVersion().isBlank()) {
            throw new IllegalArgumentException("marketType, stockCode, orderType, positive price/quantity, and idempotencyKey are required.");
        }
        normalizedExchangeCode(request);
    }

    private String normalizedExchangeCode(OrderRequestDto request) {
        if (request.getMarketType() != MarketType.OVERSEAS) {
            return null;
        }
        return OverseasExchange.from(request.getExchangeCode()).orderExchangeCode();
    }

    private OrderResponseDto toResponse(TradingLog order, boolean replayed) {
        boolean success = order.getStatus() == OrderStatus.SUBMITTED
                || order.getStatus() == OrderStatus.PARTIALLY_EXECUTED
                || order.getStatus() == OrderStatus.EXECUTED
                || order.getStatus() == OrderStatus.CANCEL_REQUESTED
                || order.getStatus() == OrderStatus.CANCELED
                || order.getStatus() == OrderStatus.PARTIALLY_EXECUTED_CANCELED;
        return OrderResponseDto.builder()
                .success(success)
                .brokerOrderId(order.getId())
                .orderId(order.getExternalOrderId())
                .message(order.getResponseMessage())
                .status(order.getStatus())
                .replayed(replayed)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradingLog> getDailyLogs() {
        var range = tradingTimeService.currentUtcDay();
        return tradingLogRepository.findAllByCreatedAtRange(range.startInclusive(), range.endExclusive());
    }
}
