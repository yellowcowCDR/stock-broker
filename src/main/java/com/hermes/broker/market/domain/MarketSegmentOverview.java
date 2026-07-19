package com.hermes.broker.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketSegmentOverview(
        String segment,
        String indexCode,
        BigDecimal indexValue,
        BigDecimal indexChangeRate,
        BigDecimal accumulatedTradingValue,
        long advancingIssues,
        long decliningIssues,
        long unchangedIssues,
        long upperLimitIssues,
        long lowerLimitIssues,
        BigDecimal breadthScore,
        BigDecimal foreignNetBuyTradingValue,
        BigDecimal individualNetBuyTradingValue,
        BigDecimal institutionNetBuyTradingValue,
        String tradingValueUnit,
        LocalDate observedMarketDate
) {
}
