package com.hermes.broker.market.domain;

public record FinancialStatement(
        String businessYear,
        String reportCode,
        String accountName,
        String amount,
        String currency
) {
}
