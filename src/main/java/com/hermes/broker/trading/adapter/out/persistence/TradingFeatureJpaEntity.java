package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.domain.MarketType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trading_feature_snapshot", uniqueConstraints =
        @UniqueConstraint(name = "uk_trading_feature_idempotency_ddl", columnNames = "idempotency_key"))
@Getter
@Setter
public class TradingFeatureJpaEntity {
    
    @Id
    @Column(name = "feature_id", length = 36)
    private String featureId = UUID.randomUUID().toString();

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", length = 20)
    private MarketType marketType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technical_features", columnDefinition = "jsonb")
    private Map<String, Object> technicalFeatures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "news_features", columnDefinition = "jsonb")
    private Map<String, Object> newsFeatures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_features", columnDefinition = "jsonb")
    private Map<String, Object> riskFeatures;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;
    
    @PrePersist
    protected void onCreate() {
        if (this.snapshotAt == null) {
            this.snapshotAt = Instant.now();
        }
    }
}
