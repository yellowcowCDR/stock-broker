package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.StockNewsArticle;
import java.util.List;

public interface NaverNewsSearchPort {
    List<StockNewsArticle> searchNewsByKeyword(String keyword, int display);
}
