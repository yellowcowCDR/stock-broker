package com.hermes.broker.trading.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "trading_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_trading_logs_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_trading_logs_cancel_idempotency_key", columnNames = "cancel_idempotency_key"),
        @UniqueConstraint(name = "uk_trading_logs_decision_id_ddl", columnNames = "decision_id")
}, indexes = {
        @Index(name = "idx_trading_logs_market_context_id", columnList = "market_context_id"),
        @Index(name = "idx_trading_logs_decision_id", columnList = "decision_id"),
        @Index(name = "idx_trading_logs_market_exchange_stock",
                columnList = "market_type, exchange_code, stock_code")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketType marketType;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(name = "exchange_code", length = 10)
    private String exchangeCode;

    @Column(name = "account_key", length = 100)
    private String accountKey;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "external_order_id", length = 100)
    private String externalOrderId;

    @Column(name = "market_context_id", length = 36)
    private String marketContextId;

    @Column(name = "decision_id", length = 36)
    private String decisionId;

    @Column(name = "feature_id", length = 36)
    private String featureId;

    @Column(name = "strategy_version", length = 50)
    private String strategyVersion;

    @Column(name = "cancel_idempotency_key", length = 160)
    private String cancelIdempotencyKey;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderType orderType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal orderPrice;

    @Column(nullable = false)
    private Integer orderQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal executionPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal executedQuantity;

    @Column(name = "transaction_cost", precision = 19, scale = 4)
    private BigDecimal transactionCost;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency;

    @Column(name = "cost_source", length = 100)
    private String costSource;

    @Column(name = "cost_data_complete", nullable = false)
    private boolean costDataComplete;

    @Column(name = "slippage_amount", precision = 19, scale = 4)
    private BigDecimal slippageAmount;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant submittedAt;

    @Column(length = 1000)
    private String responseMessage;

    @Column(length = 50)
    private String riskPolicyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> snapshotIndicators;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    @Builder
    public TradingLog(MarketType marketType, String stockCode, String exchangeCode,
                      String stockName, String accountKey,
                      String idempotencyKey, String requestHash, OrderType orderType,
                      BigDecimal orderPrice, Integer orderQuantity, BigDecimal executionPrice,
                      OrderStatus status, Map<String, Object> snapshotIndicators, String decisionReason,
                      String riskPolicyVersion, String decisionId, String featureId,
                      String strategyVersion) {
        this.marketType = marketType;
        this.stockCode = stockCode;
        this.exchangeCode = exchangeCode;
        this.stockName = stockName;
        this.accountKey = accountKey;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.orderType = orderType;
        this.orderPrice = orderPrice;
        this.orderQuantity = orderQuantity;
        this.executionPrice = executionPrice;
        this.status = status;
        this.snapshotIndicators = snapshotIndicators;
        this.decisionReason = decisionReason;
        this.riskPolicyVersion = riskPolicyVersion;
        this.decisionId = decisionId;
        this.featureId = featureId;
        this.strategyVersion = strategyVersion;
        this.costDataComplete = false;
        this.createdAt = Instant.now();
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public void updateExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }

    public void updateExecution(BigDecimal executionPrice, BigDecimal executedQuantity) {
        this.executionPrice = executionPrice;
        this.executedQuantity = executedQuantity;
    }

    public void updateRiskSnapshot(Map<String, Object> riskSnapshot) {
        this.snapshotIndicators = riskSnapshot;
    }

    public void linkMarketContext(String marketContextId) {
        this.marketContextId = marketContextId;
    }

    public void reconcileExecutionCost(BigDecimal transactionCost, String currency,
                                       String source, Instant reconciledAt) {
        if (transactionCost == null || transactionCost.signum() < 0) {
            throw new IllegalArgumentException("KIS transaction cost must be zero or positive.");
        }
        this.transactionCost = transactionCost;
        this.costCurrency = currency;
        this.costSource = source;
        this.slippageAmount = calculateAdverseSlippage();
        this.reconciledAt = reconciledAt;
        this.costDataComplete = true;
    }

    public void reconcileNoExecution(String source, Instant reconciledAt) {
        if (executedQuantity != null && executedQuantity.signum() > 0) {
            throw new IllegalStateException("An executed order cannot be reconciled as no-execution.");
        }
        this.costSource = source;
        this.reconciledAt = reconciledAt;
        this.costDataComplete = true;
    }

    private BigDecimal calculateAdverseSlippage() {
        if (executionPrice == null || executedQuantity == null || executedQuantity.signum() <= 0) {
            return null;
        }
        BigDecimal perShare = orderType == OrderType.BUY
                ? executionPrice.subtract(orderPrice)
                : orderPrice.subtract(executionPrice);
        return perShare.multiply(executedQuantity);
    }

    public void markRejected(String message) {
        this.status = OrderStatus.REJECTED;
        this.responseMessage = message;
    }

    public void markSubmitting() {
        this.status = OrderStatus.SUBMITTING;
    }

    public void markSubmitted(String externalOrderId, String message) {
        this.status = OrderStatus.SUBMITTED;
        this.externalOrderId = externalOrderId;
        this.responseMessage = message;
        this.submittedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.status = OrderStatus.FAILED;
        this.responseMessage = message;
    }

    public void markUnknown(String message) {
        this.status = OrderStatus.UNKNOWN;
        this.responseMessage = message;
    }

    public void markCancelRequested(String cancelIdempotencyKey) {
        this.status = OrderStatus.CANCEL_REQUESTED;
        this.cancelIdempotencyKey = cancelIdempotencyKey;
    }

    public void markCanceled(String message) {
        this.status = OrderStatus.CANCELED;
        this.responseMessage = message;
    }

    public void markCancellationAccepted(String message) {
        this.status = OrderStatus.CANCEL_REQUESTED;
        this.responseMessage = message;
    }
}
