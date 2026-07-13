package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trading_decision")
@Getter
@Setter
public class TradingDecisionJpaEntity {

    @Id
    @Column(name = "decision_id", length = 36)
    private String decisionId = UUID.randomUUID().toString();

    @Column(name = "feature_id", length = 36, nullable = false)
    private String featureId;

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", length = 20, nullable = false)
    private TradingDecisionType decisionType;

    @Column(name = "strategy_version", length = 50, nullable = false)
    private String strategyVersion;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "recommended_price", precision = 20, scale = 4)
    private BigDecimal recommendedPrice;

    @Column(name = "recommended_quantity", precision = 20, scale = 4)
    private BigDecimal recommendedQuantity;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        if (this.decidedAt == null) {
            this.decidedAt = LocalDateTime.now();
        }
    }
}
