package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.NewsSearchSnapshot;

public interface NaverNewsSearchPort {
    NewsSearchSnapshot searchNewsByKeyword(String keyword, int display);
}
