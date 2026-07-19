package com.hermes.broker.market.domain;

import java.time.LocalDate;
import java.util.Map;

public record UsFinancialReport(
        String statementType,
        String periodType,
        LocalDate fiscalDateEnding,
        String reportedCurrency,
        Map<String, String> values
) {
    public UsFinancialReport {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
