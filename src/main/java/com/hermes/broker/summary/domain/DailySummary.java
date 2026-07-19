package com.hermes.broker.summary.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import com.hermes.broker.trading.domain.MarketType;

@Entity
@Table(name = "daily_summaries", uniqueConstraints =
        @UniqueConstraint(name = "uk_daily_summaries_market_date", columnNames = {"market_type", "trade_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 20)
    private MarketType marketType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal closingTotalAsset;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal dailyReturnRate;

    @Column(nullable = false)
    private Integer totalTradeCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String retrospectiveReport;

    @Builder
    public DailySummary(LocalDate tradeDate, MarketType marketType,
                        BigDecimal closingTotalAsset, BigDecimal dailyReturnRate,
                        Integer totalTradeCount, String retrospectiveReport) {
        this.tradeDate = tradeDate;
        this.marketType = marketType;
        this.closingTotalAsset = closingTotalAsset;
        this.dailyReturnRate = dailyReturnRate;
        this.totalTradeCount = totalTradeCount;
        this.retrospectiveReport = retrospectiveReport;
        this.createdAt = Instant.now();
    }
}
