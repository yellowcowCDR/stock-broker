package com.hermes.broker.agent.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.evaluatedAt == null) {
            this.evaluatedAt = LocalDateTime.now();
        }
    }
}
