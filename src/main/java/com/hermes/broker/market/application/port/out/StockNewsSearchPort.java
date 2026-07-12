package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.StockNews;
import java.util.List;

public interface StockNewsSearchPort {
    List<StockNews> searchLatestNews(String stockCode);
}
