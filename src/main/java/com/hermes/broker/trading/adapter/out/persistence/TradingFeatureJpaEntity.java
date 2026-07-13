package com.hermes.broker.trading.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trading_feature_snapshot")
@Getter
@Setter
public class TradingFeatureJpaEntity {
    
    @Id
    @Column(name = "feature_id", length = 36)
    private String featureId = UUID.randomUUID().toString();

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

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
    private LocalDateTime snapshotAt;
    
    @PrePersist
    protected void onCreate() {
        if (this.snapshotAt == null) {
            this.snapshotAt = LocalDateTime.now();
        }
    }
}
