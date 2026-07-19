package com.hermes.broker.market.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UsFundamentalsSnapshot(
        String symbol,
        Map<String, String> companyOverview,
        List<UsFinancialReport> financialReports,
        List<UsHistoricalEarnings> earningsHistory,
        UsEarningsSchedule upcomingEarnings,
        List<String> dataSources,
        Instant fetchedAt,
        Instant validUntil,
        boolean financialDataComplete,
        boolean earningsCalendarComplete,
        boolean announcementTimeComplete,
        boolean complete,
        List<String> warnings
) {
    public UsFundamentalsSnapshot {
        companyOverview = companyOverview == null ? Map.of() : Map.copyOf(companyOverview);
        financialReports = financialReports == null ? List.of() : List.copyOf(financialReports);
        earningsHistory = earningsHistory == null ? List.of() : List.copyOf(earningsHistory);
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
