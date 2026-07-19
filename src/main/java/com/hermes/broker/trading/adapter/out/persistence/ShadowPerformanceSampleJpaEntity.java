package com.hermes.broker.trading.adapter.out.persistence;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "shadow_performance_sample", uniqueConstraints =
        @UniqueConstraint(name = "uk_shadow_sample_decision_ddl", columnNames = "decision_id"))
@Getter
@Setter
public class ShadowPerformanceSampleJpaEntity {
    @Id
    @Column(name = "sample_id", length = 36)
    private String sampleId;

    @Column(name = "decision_id", length = 36, nullable = false)
    private String decisionId;

    @Column(name = "feature_id", length = 36, nullable = false)
    private String featureId;

    @Column(name = "strategy_version", length = 50, nullable = false)
    private String strategyVersion;

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", length = 20, nullable = false)
    private MarketType marketType;

    @Column(name = "exchange_code", length = 10)
    private String exchangeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", length = 20, nullable = false)
    private TradingDecisionType decisionType;

    @Column(name = "reference_price", precision = 20, scale = 4, nullable = false)
    private BigDecimal referencePrice;

    @Column(name = "observed_price", precision = 20, scale = 4)
    private BigDecimal observedPrice;

    @Column(name = "raw_return_rate", precision = 20, scale = 6)
    private BigDecimal rawReturnRate;

    @Column(name = "action_return_rate", precision = 20, scale = 6)
    private BigDecimal actionReturnRate;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_status", length = 20, nullable = false)
    private ShadowSampleStatus status;

    @Column(name = "data_source", length = 100, nullable = false)
    private String dataSource;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "observed_at")
    private Instant observedAt;
}
