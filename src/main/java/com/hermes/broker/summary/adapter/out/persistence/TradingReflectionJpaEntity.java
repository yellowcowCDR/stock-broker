package com.hermes.broker.summary.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.hermes.broker.summary.domain.TradeReview;
import com.hermes.broker.trading.domain.MarketType;

@Entity
@Table(name = "trading_reflection", uniqueConstraints = @UniqueConstraint(
        name = "uk_trading_reflection_identity",
        columnNames = {"trading_date", "market_type", "strategy_version"}))
@Getter
@Setter
public class TradingReflectionJpaEntity {

    @Id
    @Column(name = "reflection_id", length = 36)
    private String reflectionId = UUID.randomUUID().toString();

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 20)
    private MarketType marketType;

    @Column(name = "market_zone_id", nullable = false, length = 50)
    private String marketZoneId;

    @Column(name = "strategy_version", length = 50, nullable = false)
    private String strategyVersion;

    @Column(name = "daily_return_rate", precision = 20, scale = 4)
    private BigDecimal dailyReturnRate;

    @Column(name = "market_return_rate", precision = 20, scale = 4)
    private BigDecimal marketReturnRate;

    @Column(name = "total_transaction_cost", precision = 20, scale = 4)
    private BigDecimal totalTransactionCost;

    @Column(name = "total_slippage_amount", precision = 20, scale = 4)
    private BigDecimal totalSlippageAmount;

    @Column(name = "decision_count", nullable = false)
    private int decisionCount;

    @Column(name = "hold_count", nullable = false)
    private int holdCount;

    @Column(name = "block_count", nullable = false)
    private int blockCount;

    @Column(name = "data_complete", nullable = false)
    private boolean dataComplete;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reviews", columnDefinition = "jsonb")
    private List<TradeReview> reviews;

    @Column(name = "overall_feedback", length = 2000)
    private String overallFeedback;

    @Column(name = "improvement_plan", length = 2000)
    private String improvementPlan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
