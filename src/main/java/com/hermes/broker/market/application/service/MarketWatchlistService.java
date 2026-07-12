package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.GetMarketWatchlistUseCase;
import com.hermes.broker.market.domain.WatchlistStock;
import com.hermes.broker.market.domain.WatchlistCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MarketWatchlistService implements GetMarketWatchlistUseCase {

    @Override
    public List<WatchlistStock> getWatchlist() {
        return List.of(
                createMockCoreStock("005930", "삼성전자", "KRX", "70", List.of("시가총액 상위", "높은 유동성", "시장 대표 종목")),
                createMockCoreStock("000660", "SK하이닉스", "KRX", "75", List.of("AI 반도체 수요 증가", "높은 유동성")),
                createMockCoreStock("AAPL", "Apple", "NASDAQ", "80", List.of("글로벌 시총 1위", "안정적인 실적")),
                createMockCoreStock("TSLA", "Tesla", "NASDAQ", "65", List.of("변동성 우수", "전기차 대표 주자")),
                createMockCoreStock("MSFT", "Microsoft", "NASDAQ", "85", List.of("클라우드 성장", "AI 분야 선도")),
                createMockCoreStock("NVDA", "NVIDIA", "NASDAQ", "90", List.of("AI 반도체 시장 장악", "실적 모멘텀"))
        );
    }

    private WatchlistStock createMockCoreStock(String code, String name, String market, String score, List<String> reasons) {
        return new WatchlistStock(code, name, market, WatchlistCategory.CORE, new BigDecimal(score), reasons);
    }
}
