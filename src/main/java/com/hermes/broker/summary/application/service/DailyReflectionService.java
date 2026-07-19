package com.hermes.broker.summary.application.service;

import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.time.TimeRange;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.domain.MarketSegmentOverview;
import com.hermes.broker.summary.application.port.in.RunDailyReflectionUseCase;
import com.hermes.broker.summary.application.port.out.DailySummaryRepository;
import com.hermes.broker.summary.application.port.out.LoadTradingReflectionPort;
import com.hermes.broker.summary.application.port.out.SaveTradingReflectionPort;
import com.hermes.broker.summary.domain.DailySummary;
import com.hermes.broker.summary.domain.TradeReview;
import com.hermes.broker.summary.domain.TradingReflection;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingDecisionMode;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DailyReflectionService implements RunDailyReflectionUseCase {

    private final TradingTimeService tradingTimeService;
    private final LoadTradingDecisionPort loadTradingDecisionPort;
    private final LoadTradingFeaturePort loadTradingFeaturePort;
    private final TradingLogRepository tradingLogRepository;
    private final LoadMarketContextPort loadMarketContextPort;
    private final DailySummaryRepository dailySummaryRepository;
    private final LoadTradingReflectionPort loadTradingReflectionPort;
    private final SaveTradingReflectionPort saveTradingReflectionPort;

    @Override
    @Transactional
    public List<TradingReflection> runDailyReflection(MarketType requestedMarketType,
                                                      LocalDate requestedTradingDate) {
        MarketType marketType = requestedMarketType == null ? MarketType.DOMESTIC : requestedMarketType;
        LocalDate tradingDate = requestedTradingDate == null
                ? tradingTimeService.currentMarketDate(marketType) : requestedTradingDate;
        TimeRange range = tradingTimeService.day(tradingDate, tradingTimeService.zoneFor(marketType));

        List<TradingDecision> allDecisions = loadTradingDecisionPort
                .loadBetween(range.startInclusive(), range.endExclusive()).stream()
                .filter(decision -> decision.mode() == TradingDecisionMode.ACTIVE)
                .toList();
        Map<String, TradingFeatureSnapshot> features = new LinkedHashMap<>();
        for (TradingDecision decision : allDecisions) {
            if (decision.featureId() == null || decision.featureId().isBlank()) {
                throw unavailable("Decision " + decision.decisionId() + " has no feature linkage.");
            }
            TradingFeatureSnapshot feature = loadTradingFeaturePort.loadById(decision.featureId())
                    .orElseThrow(() -> unavailable("Feature " + decision.featureId()
                            + " referenced by decision " + decision.decisionId() + " does not exist."));
            features.put(feature.featureId(), feature);
        }
        List<TradingDecision> decisions = allDecisions.stream()
                .filter(decision -> features.get(decision.featureId()).marketType() == marketType)
                .toList();
        if (decisions.isEmpty()) {
            throw unavailable("No real decisions exist for " + marketType + " on " + tradingDate + ".");
        }

        List<TradingLog> marketLogs = tradingLogRepository
                .findAllByCreatedAtRange(range.startInclusive(), range.endExclusive())
                .stream()
                .filter(log -> log.getMarketType() == marketType)
                .toList();
        rejectUnlinkedOrders(marketLogs, marketType, tradingDate);
        Map<String, TradingLog> logsByDecision = indexLogs(marketLogs);

        List<String> strategyVersions = decisions.stream()
                .map(TradingDecision::strategyVersion)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (strategyVersions.size() != 1 || decisions.stream().anyMatch(
                decision -> decision.strategyVersion() == null || decision.strategyVersion().isBlank())) {
            throw unavailable("A market-day reflection requires exactly one non-empty strategy version; "
                    + "portfolio return attribution across strategies is unavailable.");
        }

        DailySummary summary = dailySummaryRepository
                .findByMarketTypeAndTradeDate(marketType, tradingDate)
                .orElseThrow(() -> unavailable("A completed " + marketType
                        + " daily asset summary is missing for " + tradingDate + "."));
        if (summary.getDailyReturnRate() == null) {
            throw unavailable("Daily return is missing from the completed asset summary.");
        }

        Map<String, MarketContext> contexts = new HashMap<>();
        List<TradeReview> reviews = new ArrayList<>();
        for (TradingDecision decision : decisions) {
            TradingFeatureSnapshot feature = requireFeature(features, decision);
            TradingLog order = logsByDecision.get(decision.decisionId());
            validateDecisionOrderRelationship(decision, feature, order);
            String contextId = resolveContextId(feature, order);
            MarketContext context = loadAndValidateContext(contextId, marketType, decision.decidedAt());
            contexts.putIfAbsent(context.contextId(), context);
            reviews.add(toReview(decision, feature, order, context.contextId()));
        }

        BigDecimal marketReturn = calculateMarketReturn(contexts.values().stream().toList());
        BigDecimal totalCosts = reviews.stream()
                .map(TradeReview::transactionCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSlippage = reviews.stream()
                .map(TradeReview::slippageAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int holdCount = (int) decisions.stream()
                .filter(decision -> decision.decisionType() == TradingDecisionType.HOLD).count();
        int blockCount = (int) decisions.stream()
                .filter(decision -> decision.decisionType() == TradingDecisionType.BLOCK).count();

        String strategyVersion = strategyVersions.get(0);
        String reflectionId = loadTradingReflectionPort
                .loadByIdentity(tradingDate, marketType, strategyVersion)
                .map(TradingReflection::reflectionId)
                .orElseGet(() -> UUID.randomUUID().toString());
        TradingReflection reflection = new TradingReflection(
                reflectionId,
                tradingDate,
                marketType,
                tradingTimeService.zoneFor(marketType).getId(),
                strategyVersion,
                summary.getDailyReturnRate(),
                marketReturn,
                totalCosts,
                totalSlippage,
                decisions.size(),
                holdCount,
                blockCount,
                true,
                List.copyOf(reviews),
                factualFeedback(decisions, reviews, totalCosts, totalSlippage),
                "No strategy change was generated automatically. Use only complete Broker reflections in shadow evaluation.",
                Instant.now()
        );
        saveTradingReflectionPort.save(reflection);
        return List.of(reflection);
    }

    private void rejectUnlinkedOrders(List<TradingLog> logs, MarketType marketType, LocalDate tradingDate) {
        List<Long> unlinked = logs.stream()
                .filter(log -> log.getDecisionId() == null || log.getDecisionId().isBlank()
                        || log.getFeatureId() == null || log.getFeatureId().isBlank()
                        || log.getStrategyVersion() == null || log.getStrategyVersion().isBlank())
                .map(TradingLog::getId)
                .toList();
        if (!unlinked.isEmpty()) {
            throw unavailable("Orders without decision/feature/strategy linkage exist for "
                    + marketType + " on " + tradingDate + ": " + unlinked);
        }
    }

    private Map<String, TradingLog> indexLogs(List<TradingLog> logs) {
        Map<String, TradingLog> indexed = new HashMap<>();
        for (TradingLog log : logs) {
            TradingLog previous = indexed.putIfAbsent(log.getDecisionId(), log);
            if (previous != null) {
                throw unavailable("Multiple orders reference decision " + log.getDecisionId()
                        + "; one-decision/one-order attribution is required.");
            }
        }
        return indexed;
    }

    private TradingFeatureSnapshot requireFeature(Map<String, TradingFeatureSnapshot> features,
                                                   TradingDecision decision) {
        TradingFeatureSnapshot feature = features.get(decision.featureId());
        if (feature == null) {
            throw unavailable("Feature " + decision.featureId() + " is unavailable at decision time.");
        }
        if (feature.snapshotAt() == null || decision.decidedAt() == null
                || feature.snapshotAt().isAfter(decision.decidedAt())) {
            throw unavailable("Feature " + feature.featureId() + " was not available at decision time.");
        }
        return feature;
    }

    private void validateDecisionOrderRelationship(TradingDecision decision,
                                                   TradingFeatureSnapshot feature,
                                                   TradingLog order) {
        boolean orderDecision = decision.decisionType() == TradingDecisionType.BUY
                || decision.decisionType() == TradingDecisionType.SELL;
        if (orderDecision && order == null) {
            throw unavailable("No Broker order outcome exists for decision " + decision.decisionId() + ".");
        }
        if (!orderDecision && order != null) {
            throw unavailable("HOLD/BLOCK decision " + decision.decisionId() + " unexpectedly has an order.");
        }
        if (order == null) {
            return;
        }
        if (!Objects.equals(order.getFeatureId(), feature.featureId())
                || !Objects.equals(order.getStrategyVersion(), decision.strategyVersion())) {
            throw unavailable("Order audit linkage does not match decision " + decision.decisionId() + ".");
        }
        if (isUnresolved(order.getStatus())) {
            throw unavailable("Order " + order.getId() + " is not terminal: " + order.getStatus() + ".");
        }
        if (hasExecution(order) && (!order.isCostDataComplete()
                || order.getTransactionCost() == null
                || order.getCostCurrency() == null
                || order.getCostSource() == null
                || order.getReconciledAt() == null
                || order.getSlippageAmount() == null)) {
            throw unavailable("Executed order " + order.getId()
                    + " has incomplete KIS cost/slippage reconciliation.");
        }
    }

    private boolean isUnresolved(OrderStatus status) {
        return status == OrderStatus.VALIDATING
                || status == OrderStatus.SUBMITTING
                || status == OrderStatus.SUBMITTED
                || status == OrderStatus.PENDING
                || status == OrderStatus.PARTIALLY_EXECUTED
                || status == OrderStatus.CANCEL_REQUESTED
                || status == OrderStatus.UNKNOWN;
    }

    private boolean hasExecution(TradingLog order) {
        return order.getExecutedQuantity() != null && order.getExecutedQuantity().signum() > 0;
    }

    private String resolveContextId(TradingFeatureSnapshot feature, TradingLog order) {
        if (order != null && order.getMarketContextId() != null && !order.getMarketContextId().isBlank()) {
            return order.getMarketContextId();
        }
        Object contextId = feature.riskFeatures() == null
                ? null : feature.riskFeatures().get("marketContextId");
        if (contextId == null || contextId.toString().isBlank()) {
            throw unavailable("Market context linkage is missing for feature " + feature.featureId() + ".");
        }
        return contextId.toString();
    }

    private MarketContext loadAndValidateContext(String contextId, MarketType marketType,
                                                 Instant decidedAt) {
        MarketContext context = loadMarketContextPort.loadById(contextId)
                .orElseThrow(() -> unavailable("Market context " + contextId + " no longer exists."));
        if (context.marketType() != marketType
                || context.overviewSnapshot() == null
                || context.overviewSnapshot().marketType() != marketType
                || !context.overviewSnapshot().complete()) {
            throw unavailable("Market context " + contextId + " is incomplete or belongs to another market.");
        }
        if (context.analyzedAt() == null || context.analyzedAt().isAfter(decidedAt)
                || context.validUntil() == null || !decidedAt.isBefore(context.validUntil())
                || context.overviewSnapshot().fetchedAt() == null
                || context.overviewSnapshot().fetchedAt().isAfter(decidedAt)
                || context.overviewSnapshot().validUntil() == null
                || !decidedAt.isBefore(context.overviewSnapshot().validUntil())) {
            throw unavailable("Market context " + contextId + " was not valid at decision time.");
        }
        return context;
    }

    private TradeReview toReview(TradingDecision decision, TradingFeatureSnapshot feature,
                                 TradingLog order, String contextId) {
        return new TradeReview(
                decision.stockCode(),
                feature.marketType(),
                decision.decisionId(),
                decision.featureId(),
                contextId,
                decision.strategyVersion(),
                decision.decisionType().name(),
                order == null ? "NOT_APPLICABLE" : order.getStatus().name(),
                decision.recommendedPrice(),
                order == null ? null : order.getExecutionPrice(),
                decision.recommendedQuantity(),
                order == null ? null : order.getExecutedQuantity(),
                order == null ? null : order.getTransactionCost(),
                order == null ? null : order.getCostCurrency(),
                order == null ? null : order.getCostSource(),
                order == null ? null : order.getSlippageAmount(),
                feature.snapshotAt(),
                decision.decidedAt(),
                order == null ? null : order.getCreatedAt(),
                true,
                order != null && order.getResponseMessage() != null
                        ? decision.reason() + " | Broker outcome: " + order.getResponseMessage()
                        : decision.reason()
        );
    }

    private BigDecimal calculateMarketReturn(List<MarketContext> contexts) {
        List<BigDecimal> rates = contexts.stream()
                .flatMap(context -> context.overviewSnapshot().segments().stream())
                .map(MarketSegmentOverview::indexChangeRate)
                .filter(Objects::nonNull)
                .toList();
        if (rates.isEmpty()) {
            throw unavailable("Market index return is missing from the decision-time market contexts.");
        }
        return rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rates.size()), 4, RoundingMode.HALF_UP);
    }

    private String factualFeedback(List<TradingDecision> decisions, List<TradeReview> reviews,
                                   BigDecimal totalCosts, BigDecimal totalSlippage) {
        long executed = reviews.stream()
                .filter(review -> review.executedQuantity() != null
                        && review.executedQuantity().signum() > 0)
                .count();
        long rejected = reviews.stream()
                .filter(review -> OrderStatus.REJECTED.name().equals(review.orderStatus())
                        || OrderStatus.FAILED.name().equals(review.orderStatus()))
                .count();
        return "Broker-observed decisions=" + decisions.size()
                + ", executedOrders=" + executed
                + ", rejectedOrFailedOrders=" + rejected
                + ", combinedTransactionCost=" + totalCosts.toPlainString()
                + ", adverseSlippage=" + totalSlippage.toPlainString() + ".";
    }

    private DataPipelineUnavailableException unavailable(String message) {
        return new DataPipelineUnavailableException(message + " Synthetic fallback is disabled.");
    }
}
