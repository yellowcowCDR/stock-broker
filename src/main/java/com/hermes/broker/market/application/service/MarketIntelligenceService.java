package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.MarketFundamentalUseCase;
import com.hermes.broker.market.application.port.in.MarketIntelligenceUseCase;
import com.hermes.broker.market.application.port.in.MarketNewsUseCase;
import com.hermes.broker.market.domain.StockIntelligence;
import com.hermes.broker.market.dto.response.FundamentalsResponseDto;
import com.hermes.broker.market.dto.response.IntelligenceResponseDto;
import com.hermes.broker.market.dto.response.NewsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIntelligenceService implements MarketIntelligenceUseCase {

    private final MarketFundamentalUseCase marketFundamentalUseCase;
    private final MarketNewsUseCase marketNewsUseCase;

    @Override
    public IntelligenceResponseDto getIntelligence(String stockCode) {
        FundamentalsResponseDto fundamentals = marketFundamentalUseCase.getFundamentals(stockCode);
        
        // 뉴스는 종목명을 우선적으로 사용하여 검색하도록 개선 가능
        String searchKeyword = stockCode;
        if (fundamentals.profile() != null && fundamentals.profile().stockName() != null) {
            searchKeyword = fundamentals.profile().stockName();
        }
        
        NewsResponseDto news = marketNewsUseCase.getNews(stockCode, searchKeyword);

        StockIntelligence intelligence = new StockIntelligence(
                stockCode,
                fundamentals.profile(),
                fundamentals.recentDisclosures(),
                fundamentals.recentFinancials(),
                news.result()
        );

        return new IntelligenceResponseDto(intelligence);
    }
}
