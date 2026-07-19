package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.common.property.NaverNewsProperties;
import com.hermes.broker.market.application.port.out.NaverNewsSearchPort;
import com.hermes.broker.market.domain.NewsSearchSnapshot;
import com.hermes.broker.market.domain.NewsSentiment;
import com.hermes.broker.market.domain.StockNewsArticle;
import com.hermes.broker.market.dto.response.NewsResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MarketNewsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T02:00:00Z");

    @Mock
    private NaverNewsSearchPort naverNewsSearchPort;

    private MarketNewsService marketNewsService;

    @BeforeEach
    void setUp() {
        NaverNewsProperties properties = new NaverNewsProperties(
                true, "https://openapi.naver.com", "id", "secret",
                Duration.ofSeconds(3), Duration.ofSeconds(5), 20, 100,
                Duration.ofMinutes(10), Duration.ofHours(24)
        );
        marketNewsService = new MarketNewsService(
                naverNewsSearchPort,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void getNews_preservesSourceMetadataAndAnalyzesRealApiArticles() {
        String stockCode = "005930";
        List<StockNewsArticle> sourceArticles = List.of(
                article("<b>삼성전자</b> 최대 실적 발표", "삼성전자 성장과 실적 개선이 확인됐다.", "url1"),
                article("<b>삼성전자</b> 최대 실적 발표", "중복 기사", "url2"),
                article("애플 vs 삼성전자", "스마트폰 경쟁 심화와 하락 우려", "url3")
        );
        given(naverNewsSearchPort.searchNewsByKeyword(eq("삼성전자"), anyInt()))
                .willReturn(new NewsSearchSnapshot(
                        "삼성전자", sourceArticles, 321, NOW, "NAVER_SEARCH_NEWS_API", true
                ));

        NewsResponseDto result = marketNewsService.getNews(stockCode, "삼성전자");

        assertThat(result.result().stockCode()).isEqualTo(stockCode);
        assertThat(result.result().totalAvailable()).isEqualTo(321);
        assertThat(result.result().totalAnalyzed()).isEqualTo(2);
        assertThat(result.result().dataSource()).isEqualTo("NAVER_SEARCH_NEWS_API");
        assertThat(result.result().fetchedAt()).isEqualTo(NOW);
        assertThat(result.result().complete()).isTrue();
        assertThat(result.result().freshness()).isEqualTo("FRESH");
        assertThat(result.result().analysisMethod()).isEqualTo("RULE_BASED_LEXICAL_V1");

        StockNewsArticle first = result.result().articles().get(0);
        assertThat(first.title()).isEqualTo("삼성전자 최대 실적 발표");
        assertThat(first.originalUrl()).isEqualTo("https://news.example.com/original");
        assertThat(first.relevanceScore()).isGreaterThan(0.0);
        assertThat(first.sentiment()).isEqualTo(NewsSentiment.POSITIVE);
    }

    @Test
    void getNews_rejectsIncompleteResultInsteadOfReturningPlaceholder() {
        given(naverNewsSearchPort.searchNewsByKeyword(eq("005930"), anyInt()))
                .willReturn(new NewsSearchSnapshot(
                        "005930", List.of(), 0, NOW, "NAVER_SEARCH_NEWS_API", false
                ));

        assertThatThrownBy(() -> marketNewsService.getNews("005930"))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    private StockNewsArticle article(String title, String description, String url) {
        return new StockNewsArticle(
                title,
                description,
                url,
                "https://news.example.com/original",
                "news.example.com",
                NOW.minus(Duration.ofHours(1)),
                0.0,
                0.0,
                0.0,
                NewsSentiment.NEUTRAL
        );
    }
}
