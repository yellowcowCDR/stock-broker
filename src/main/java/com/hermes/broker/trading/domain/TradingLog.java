package com.hermes.broker.trading.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "trading_logs")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> snapshotIndicators;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    @Builder
    public TradingLog(MarketType marketType, String stockCode, String stockName, OrderType orderType,
                      BigDecimal orderPrice, Integer orderQuantity, BigDecimal executionPrice,
                      OrderStatus status, Map<String, Object> snapshotIndicators, String decisionReason) {
        this.marketType = marketType;
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.orderType = orderType;
        this.orderPrice = orderPrice;
        this.orderQuantity = orderQuantity;
        this.executionPrice = executionPrice;
        this.status = status;
        this.snapshotIndicators = snapshotIndicators;
        this.decisionReason = decisionReason;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public void updateExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }
}
