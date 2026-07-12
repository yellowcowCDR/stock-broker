package com.hermes.broker.market.application.port.in;

import com.hermes.broker.market.domain.StockNews;
import java.util.List;

public interface GetStockNewsUseCase {
    List<StockNews> getNews(String stockCode);
}
