package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.InvalidStockCodeException;
import com.hermes.broker.market.application.port.out.StockNewsSearchPort;
import com.hermes.broker.market.domain.NewsSentiment;
import com.hermes.broker.market.domain.StockNews;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class StockNewsServiceTest {

    private final StockNewsSearchPort port = mock(StockNewsSearchPort.class);
    private final StockNewsService service = new StockNewsService(port);

    @Test
    void getNews_returnsMockedNews() {
        given(port.searchLatestNews(anyString())).willReturn(List.of(
                new StockNews("Title 1", "Content 1", NewsSentiment.POSITIVE, null),
                new StockNews("Title 2", "Content 2", NewsSentiment.NEGATIVE, null),
                new StockNews("Title 3", "Content 3", NewsSentiment.NEUTRAL, null)
        ));

        List<StockNews> news = service.getNews("005930");

        assertThat(news).hasSize(3);
        assertThat(news).extracting(StockNews::sentiment)
                .containsExactlyInAnyOrder(NewsSentiment.POSITIVE, NewsSentiment.NEGATIVE, NewsSentiment.NEUTRAL);
    }

    @Test
    void getNews_throwsExceptionWhenStockCodeIsBlank() {
        assertThatThrownBy(() -> service.getNews(""))
                .isInstanceOf(InvalidStockCodeException.class)
                .hasMessage("stockCode is required");
    }
}
