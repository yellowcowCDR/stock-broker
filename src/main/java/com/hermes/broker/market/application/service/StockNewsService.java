package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.InvalidStockCodeException;
import com.hermes.broker.market.application.port.in.GetStockNewsUseCase;
import com.hermes.broker.market.application.port.out.StockNewsSearchPort;
import com.hermes.broker.market.domain.NewsSentiment;
import com.hermes.broker.market.domain.StockNews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        return stockNewsSearchPort.searchLatestNews(stockCode);
    }
}
