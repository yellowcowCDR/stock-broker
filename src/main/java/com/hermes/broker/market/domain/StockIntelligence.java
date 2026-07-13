package com.hermes.broker.market.domain;

import java.util.List;

public record StockIntelligence(
        String stockCode,
        CorporateProfile profile,
        List<CorporateDisclosure> recentDisclosures,
        List<FinancialStatement> recentFinancials,
        StockNewsResult newsAnalysis
) {
}
