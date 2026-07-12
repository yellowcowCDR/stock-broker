package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.InvalidStockCodeException;
import com.hermes.broker.market.application.port.in.GetStockNewsUseCase;
import com.hermes.broker.market.application.port.out.StockNewsSearchPort;
import com.hermes.broker.market.domain.NewsSentiment;
import com.hermes.broker.market.domain.StockNews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockNewsService implements GetStockNewsUseCase {

    private final StockNewsSearchPort stockNewsSearchPort; // 향후 연동을 위한 의존성

    @Override
    public List<StockNews> getNews(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new InvalidStockCodeException("stockCode is required");
        }

        // 현재는 실제 Outbound Port를 호출하지 않고 Mock 데이터를 반환합니다.
        return List.of(
                new StockNews(
                        "AI 및 반도체 투자 확대 기대감",
                        "신규 투자와 수요 증가 전망으로 실적 개선 기대감이 확대되고 있다.",
                        NewsSentiment.POSITIVE,
                        LocalDateTime.now().minusHours(1)
                ),
                new StockNews(
                        "글로벌 수요 둔화 우려",
                        "주요 시장의 경기 둔화 가능성으로 단기 실적 변동성이 커질 수 있다.",
                        NewsSentiment.NEGATIVE,
                        LocalDateTime.now().minusHours(2)
                ),
                new StockNews(
                        "분기 실적 발표 예정",
                        "회사는 예정된 일정에 따라 다음 분기 실적을 발표할 예정이다.",
                        NewsSentiment.NEUTRAL,
                        LocalDateTime.now().minusHours(3)
                )
        );
    }
}
