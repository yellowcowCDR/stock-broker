package com.hermes.broker.summary.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.hermes.broker.summary.domain.TradeReview;

@Entity
@Table(name = "trading_reflection")
@Getter
@Setter
public class TradingReflectionJpaEntity {

    @Id
    @Column(name = "reflection_id", length = 36)
    private String reflectionId = UUID.randomUUID().toString();

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(name = "strategy_version", length = 50, nullable = false)
    private String strategyVersion;

    @Column(name = "daily_return_rate", precision = 20, scale = 4)
    private BigDecimal dailyReturnRate;

    @Column(name = "market_return_rate", precision = 20, scale = 4)
    private BigDecimal marketReturnRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reviews", columnDefinition = "jsonb")
    private List<TradeReview> reviews;

    @Column(name = "overall_feedback", length = 2000)
    private String overallFeedback;

    @Column(name = "improvement_plan", length = 2000)
    private String improvementPlan;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
