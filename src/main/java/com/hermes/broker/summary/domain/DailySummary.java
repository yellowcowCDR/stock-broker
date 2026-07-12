package com.hermes.broker.summary.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal closingTotalAsset;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal dailyReturnRate;

    @Column(nullable = false)
    private Integer totalTradeCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String retrospectiveReport;

    @Builder
    public DailySummary(LocalDate tradeDate, BigDecimal closingTotalAsset, BigDecimal dailyReturnRate,
                        Integer totalTradeCount, String retrospectiveReport) {
        this.tradeDate = tradeDate;
        this.closingTotalAsset = closingTotalAsset;
        this.dailyReturnRate = dailyReturnRate;
        this.totalTradeCount = totalTradeCount;
        this.retrospectiveReport = retrospectiveReport;
        this.createdAt = LocalDateTime.now();
    }
}
