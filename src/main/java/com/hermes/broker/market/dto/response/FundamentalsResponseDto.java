package com.hermes.broker.market.dto.response;

import com.hermes.broker.market.domain.CorporateDisclosure;
import com.hermes.broker.market.domain.CorporateProfile;
import com.hermes.broker.market.domain.FinancialStatement;

import java.util.List;

public record FundamentalsResponseDto(
        String stockCode,
        CorporateProfile profile,
        List<CorporateDisclosure> recentDisclosures,
        List<FinancialStatement> recentFinancials
) {
}
