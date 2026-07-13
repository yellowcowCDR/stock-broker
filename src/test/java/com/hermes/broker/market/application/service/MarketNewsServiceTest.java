package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.out.NaverNewsSearchPort;
import com.hermes.broker.market.domain.StockNewsArticle;
import com.hermes.broker.market.dto.response.NewsResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MarketNewsServiceTest {

    @Mock
    private NaverNewsSearchPort naverNewsSearchPort;

    @InjectMocks
    private MarketNewsService marketNewsService;

    @Test
    void getNews_cleansHtmlAndDeduplicates() {
        // given
        String stockCode = "005930";
        List<StockNewsArticle> mockArticles = List.of(
                new StockNewsArticle("<b>삼성전자</b> 실적 발표", "최대 실적 달성 &quot;서프라이즈&quot;", "url1", LocalDateTime.now(), 0.0),
                new StockNewsArticle("<b>삼성전자</b> 실적 발표", "중복 뉴스입니다.", "url2", LocalDateTime.now(), 0.0), // 중복 제목
                new StockNewsArticle("애플 vs 삼성", "스마트폰 경쟁 심화", "url3", LocalDateTime.now(), 0.0)
        );

        given(naverNewsSearchPort.searchNewsByKeyword(eq(stockCode), anyInt())).willReturn(mockArticles);

        // when
        NewsResponseDto result = marketNewsService.getNews(stockCode);

        // then
        assertThat(result.result().totalAnalyzed()).isEqualTo(3);
        assertThat(result.result().articles()).hasSize(2); // 중복 제거 확인
        
        StockNewsArticle firstArticle = result.result().articles().get(0);
        assertThat(firstArticle.title()).isEqualTo("삼성전자 실적 발표"); // HTML 제거 확인
        assertThat(firstArticle.description()).isEqualTo("최대 실적 달성 \"서프라이즈\""); // Entity Decode 확인
        assertThat(firstArticle.qualityScore()).isGreaterThan(0.0); // 품질 점수 계산 확인
    }
}
