package com.hermes.broker.market.adapter.out.persistence;

import com.hermes.broker.market.domain.MarketEntryPolicy;
import com.hermes.broker.trading.domain.MarketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "market_context", indexes = {
        @Index(name = "idx_market_context_market_analyzed",
                columnList = "market_type, analyzed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class MarketContextJpaEntity {

    @Id
    @Column(name = "context_id", length = 36)
    private String contextId;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 20)
    private MarketType marketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_policy", nullable = false, length = 30)
    private MarketEntryPolicy entryPolicy;

    @Column(name = "risk_multiplier", nullable = false, precision = 10, scale = 6)
    private BigDecimal riskMultiplier;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "overview_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> overviewSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rationale", nullable = false, columnDefinition = "jsonb")
    private List<String> rationale;

    @Column(name = "overview_data_source", nullable = false, length = 200)
    private String overviewDataSource;

    @Column(name = "overview_fetched_at", nullable = false)
    private Instant overviewFetchedAt;

    @Column(name = "analyzed_by", nullable = false, length = 100)
    private String analyzedBy;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;
}
