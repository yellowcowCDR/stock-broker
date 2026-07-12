package com.hermes.broker.summary.application.service;

import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.PortfolioDto;
import com.hermes.broker.summary.application.port.in.GenerateDailySummaryUseCase;
import com.hermes.broker.summary.application.port.out.DailySummaryRepository;
import com.hermes.broker.summary.domain.DailySummary;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.domain.TradingLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService implements GenerateDailySummaryUseCase {

    private final TradingLogRepository tradingLogRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final List<MarketTradingPort> marketTradingPorts;

    @Override
    @Transactional
    public void generateDailySummary() {
        LocalDate today = LocalDate.now();

        if (dailySummaryRepository.findByTradeDate(today).isPresent()) {
            log.warn("Daily summary for {} already exists. Skipping...", today);
            return;
        }

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        log.info("Starting daily summary generation for {}", today);

        List<TradingLog> logs = tradingLogRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(startOfDay, endOfDay);

        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("=== Daily Retrospective Report (").append(today).append(") ===\n");
        reportBuilder.append("Total Trades: ").append(logs.size()).append("\n\n");

        for (TradingLog logItem : logs) {
            reportBuilder.append(String.format("[%s] %s (%s) - %s\n",
                    logItem.getCreatedAt(), logItem.getStockName(), logItem.getStockCode(), logItem.getOrderType()));
            reportBuilder.append("Reason: ").append(logItem.getDecisionReason()).append("\n");
            reportBuilder.append(String.format("Order: %s qty at %s, Executed: %s, Status: %s\n",
                    logItem.getOrderQuantity(), logItem.getOrderPrice(), logItem.getExecutionPrice(), logItem.getStatus()));
            reportBuilder.append("Indicators: ").append(logItem.getSnapshotIndicators()).append("\n");
            reportBuilder.append("--------------------------------------------------\n");
        }

        BigDecimal closingAsset = BigDecimal.ZERO;
        for (MarketTradingPort port : marketTradingPorts) {
            try {
                PortfolioDto portfolio = port.getPortfolio();
                if (portfolio != null && portfolio.getTotalAsset() != null) {
                    closingAsset = closingAsset.add(portfolio.getTotalAsset());
                }
            } catch (Exception e) {
                log.error("Failed to fetch portfolio during daily summary. Asset will be recorded as 0 for this port.", e);
            }
        }

        BigDecimal dailyReturnRate = BigDecimal.ZERO;
        DailySummary yesterdaySummary = dailySummaryRepository.findByTradeDate(today.minusDays(1)).orElse(null);
        
        if (yesterdaySummary != null && yesterdaySummary.getClosingTotalAsset().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal prevAsset = yesterdaySummary.getClosingTotalAsset();
            BigDecimal diff = closingAsset.subtract(prevAsset);
            dailyReturnRate = diff.divide(prevAsset, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        DailySummary summary = DailySummary.builder()
                .tradeDate(today)
                .closingTotalAsset(closingAsset)
                .dailyReturnRate(dailyReturnRate)
                .totalTradeCount(logs.size())
                .retrospectiveReport(reportBuilder.toString())
                .build();

        dailySummaryRepository.save(summary);

        log.info("Successfully generated and saved daily summary for {}", today);
    }
}
