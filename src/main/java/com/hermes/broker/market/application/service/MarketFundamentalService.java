package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.MarketFundamentalUseCase;
import com.hermes.broker.market.application.port.out.LoadCorporateDisclosurePort;
import com.hermes.broker.market.application.port.out.LoadCorporateProfilePort;
import com.hermes.broker.market.application.port.out.LoadDartCorporationPort;
import com.hermes.broker.market.application.port.out.LoadFinancialStatementPort;
import com.hermes.broker.market.domain.CorporateDisclosure;
import com.hermes.broker.market.domain.CorporateProfile;
import com.hermes.broker.market.domain.FinancialStatement;
import com.hermes.broker.market.dto.response.FundamentalsResponseDto;
import com.hermes.broker.common.exception.MarketDataUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketFundamentalService implements MarketFundamentalUseCase {

    private final LoadDartCorporationPort loadDartCorporationPort;
    private final LoadCorporateProfilePort loadCorporateProfilePort;
    private final LoadCorporateDisclosurePort loadCorporateDisclosurePort;
    private final LoadFinancialStatementPort loadFinancialStatementPort;

    @Override
    public FundamentalsResponseDto getFundamentals(String stockCode) {
        // 1. Get CorpCode
        String corpCode = loadDartCorporationPort.getCorpCode(stockCode)
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No OpenDART corporation mapping exists for stock code " + stockCode + "."
                ));

        // 2. Fetch Profile
        CorporateProfile profile = loadCorporateProfilePort.loadProfile(corpCode)
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "OpenDART corporate profile is unavailable for stock code " + stockCode + "."
                ));

        // 3. Fetch Recent Disclosures
        List<CorporateDisclosure> disclosures = loadCorporateDisclosurePort.loadRecentDisclosures(corpCode);

        // 4. Fetch Recent Financials
        List<FinancialStatement> financials = loadFinancialStatementPort.loadRecentFinancialStatements(corpCode);

        return new FundamentalsResponseDto(stockCode, profile, disclosures, financials);
    }
}
