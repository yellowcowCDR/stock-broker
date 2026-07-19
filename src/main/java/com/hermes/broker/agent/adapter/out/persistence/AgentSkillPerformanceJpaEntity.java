package com.hermes.broker.agent.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "agent_skill_performance")
@Getter
@Setter
public class AgentSkillPerformanceJpaEntity {

    @Id
    @Column(name = "skill_version", length = 50)
    private String skillVersion;

    @Column(name = "win_rate", precision = 20, scale = 4)
    private BigDecimal winRate;

    @Column(name = "profit_factor", precision = 20, scale = 4)
    private BigDecimal profitFactor;

    @Column(name = "max_drawdown", precision = 20, scale = 4)
    private BigDecimal maxDrawdown;

    @Column(name = "total_return_rate", precision = 20, scale = 4)
    private BigDecimal totalReturnRate;

    @Column(name = "trade_count", nullable = false)
    private int tradeCount;

    @Column(name = "evaluation_days", nullable = false)
    private int evaluationDays;

    @Column(name = "average_return_rate", precision = 20, scale = 4)
    private BigDecimal averageReturnRate;

    @Column(name = "average_profit", precision = 20, scale = 4)
    private BigDecimal averageProfit;

    @Column(name = "average_loss", precision = 20, scale = 4)
    private BigDecimal averageLoss;

    @Column(name = "profit_loss_ratio", precision = 20, scale = 4)
    private BigDecimal profitLossRatio;

    @Column(name = "sharpe_ratio", precision = 20, scale = 4)
    private BigDecimal sharpeRatio;

    @Column(name = "hold_accuracy", precision = 20, scale = 4)
    private BigDecimal holdAccuracy;

    @Column(name = "risk_block_effect", precision = 20, scale = 4)
    private BigDecimal riskBlockEffect;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.evaluatedAt == null) {
            this.evaluatedAt = Instant.now();
        }
    }
}
