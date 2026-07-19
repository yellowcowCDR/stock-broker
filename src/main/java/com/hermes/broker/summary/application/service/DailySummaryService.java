package com.hermes.broker.summary.application.service;

import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService implements GenerateDailySummaryUseCase {

    private final TradingLogRepository tradingLogRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final List<MarketTradingPort> marketTradingPorts;
    private final TradingTimeService tradingTimeService;
    private final TradingProperties tradingProperties;

    @Override
    @Transactional
    public void generateDailySummary() {
        LocalDate today = tradingTimeService.currentMarketDate(com.hermes.broker.trading.domain.MarketType.DOMESTIC);

        var marketType = com.hermes.broker.trading.domain.MarketType.DOMESTIC;
        if (dailySummaryRepository.findByMarketTypeAndTradeDate(marketType, today).isPresent()) {
            log.warn("Daily summary for {} already exists. Skipping...", today);
            return;
        }

        var tradingDay = tradingTimeService.day(today, tradingTimeService.zoneFor(
                com.hermes.broker.trading.domain.MarketType.DOMESTIC));

        log.info("Starting daily summary generation for {}", today);

        List<TradingLog> logs = tradingLogRepository.findAllByCreatedAtRange(
                tradingDay.startInclusive(), tradingDay.endExclusive());

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
            if (port.supports(com.hermes.broker.trading.domain.MarketType.OVERSEAS)
                    && (tradingProperties.overseasOrder() == null
                    || !tradingProperties.overseasOrder().enabled())) {
                continue;
            }
            PortfolioDto portfolio = port.getPortfolio();
            if (portfolio == null || portfolio.getTotalAsset() == null) {
                throw new IllegalStateException("Portfolio total asset is unavailable; daily summary was not saved.");
            }
            closingAsset = closingAsset.add(portfolio.getTotalAsset());
        }

        DailySummary yesterdaySummary = dailySummaryRepository.findLatestBefore(marketType, today)
                .orElseThrow(() -> new DataPipelineUnavailableException(
                        "Previous closing-asset baseline is missing; zero return fallback is disabled."
                ));
        if (yesterdaySummary.getClosingTotalAsset() == null
                || yesterdaySummary.getClosingTotalAsset().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DataPipelineUnavailableException(
                    "Previous closing-asset baseline is invalid; daily summary was not saved."
            );
        }
        BigDecimal prevAsset = yesterdaySummary.getClosingTotalAsset();
        BigDecimal diff = closingAsset.subtract(prevAsset);
        BigDecimal dailyReturnRate = diff.divide(prevAsset, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        DailySummary summary = DailySummary.builder()
                .tradeDate(today)
                .marketType(marketType)
                .closingTotalAsset(closingAsset)
                .dailyReturnRate(dailyReturnRate)
                .totalTradeCount(logs.size())
                .retrospectiveReport(reportBuilder.toString())
                .build();

        dailySummaryRepository.save(summary);

        log.info("Successfully generated and saved daily summary for {}", today);
    }
}
