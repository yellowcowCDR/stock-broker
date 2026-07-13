package com.hermes.broker.market.application.service;

import com.hermes.broker.market.application.port.in.MarketNewsUseCase;
import com.hermes.broker.market.application.port.out.NaverNewsSearchPort;
import com.hermes.broker.market.domain.StockNewsArticle;
import com.hermes.broker.market.domain.StockNewsResult;
import com.hermes.broker.market.dto.response.NewsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketNewsService implements MarketNewsUseCase {

    private final NaverNewsSearchPort naverNewsSearchPort;
    private static final int DEFAULT_DISPLAY = 20;

    @Override
    public NewsResponseDto getNews(String stockCode) {
        // 검색어는 일단 종목코드를 그대로 사용하거나, 필요한 경우 종목명으로 변환하는 로직을 추가할 수 있습니다.
        // 현재는 stockCode 자체를 키워드로 넘깁니다 (추후 Profile 연동 시 종목명 사용 권장).
        List<StockNewsArticle> rawArticles = naverNewsSearchPort.searchNewsByKeyword(stockCode, DEFAULT_DISPLAY);

        List<StockNewsArticle> cleanedArticles = cleanArticles(rawArticles);

        StockNewsResult result = new StockNewsResult(stockCode, cleanedArticles, rawArticles.size());
        return new NewsResponseDto(result);
    }

    private List<StockNewsArticle> cleanArticles(List<StockNewsArticle> rawArticles) {
        Set<String> titleSet = new HashSet<>();
        
        return rawArticles.stream()
                .filter(article -> titleSet.add(cleanHtml(article.title()))) // 중복 제목 제거
                .map(this::processArticle)
                .collect(Collectors.toList());
    }

    private StockNewsArticle processArticle(StockNewsArticle raw) {
        String cleanedTitle = cleanHtml(raw.title());
        String cleanedDesc = cleanHtml(raw.description());
        
        // 품질 점수는 단순하게 제목 및 본문 길이로 임시 산정 (필요시 고도화)
        double qualityScore = calculateQuality(cleanedTitle, cleanedDesc);

        return new StockNewsArticle(
                cleanedTitle,
                cleanedDesc,
                raw.url(),
                raw.publishedAt(),
                qualityScore
        );
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        // 1. HTML 태그 제거
        String noHtml = text.replaceAll("<[^>]*>", "");
        // 2. Entity Decode (&quot; -> " 등)
        return HtmlUtils.htmlUnescape(noHtml).trim();
    }
    
    private double calculateQuality(String title, String description) {
        if (title.isEmpty() || description.isEmpty()) return 0.0;
        // 제목과 내용이 충분히 긴 기사에 더 높은 점수 부여
        return Math.min(100.0, (title.length() * 0.5) + (description.length() * 0.2));
    }
}
