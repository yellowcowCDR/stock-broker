package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.common.property.NaverNewsProperties;
import com.hermes.broker.market.application.port.in.MarketNewsUseCase;
import com.hermes.broker.market.application.port.out.NaverNewsSearchPort;
import com.hermes.broker.market.domain.NewsSearchSnapshot;
import com.hermes.broker.market.domain.NewsSentiment;
import com.hermes.broker.market.domain.StockNewsArticle;
import com.hermes.broker.market.domain.StockNewsResult;
import com.hermes.broker.market.dto.response.NewsResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MarketNewsService implements MarketNewsUseCase {

    private static final String ANALYSIS_METHOD = "RULE_BASED_LEXICAL_V1";
    private static final List<String> POSITIVE_TERMS = List.of(
            "호실적", "최대 실적", "흑자", "성장", "상승", "급등", "수주", "승인", "확대",
            "개선", "돌파", "상향", "positive", "growth", "surge", "beat", "upgrade", "approval"
    );
    private static final List<String> NEGATIVE_TERMS = List.of(
            "적자", "하락", "급락", "감소", "부진", "우려", "리콜", "제재", "소송", "하향",
            "중단", "위기", "negative", "decline", "miss", "downgrade", "recall", "lawsuit"
    );

    private final NaverNewsSearchPort naverNewsSearchPort;
    private final NaverNewsProperties properties;
    private final Clock clock;

    @Override
    public NewsResponseDto getNews(String stockCode) {
        return getNews(stockCode, stockCode);
    }

    @Override
    public NewsResponseDto getNews(String stockCode, String searchQuery) {
        String query = searchQuery == null || searchQuery.isBlank() ? stockCode : searchQuery.trim();
        NewsSearchSnapshot snapshot = naverNewsSearchPort.searchNewsByKeyword(
                query,
                positive(properties.defaultDisplay(), 20)
        );
        if (snapshot == null || !snapshot.complete() || snapshot.fetchedAt() == null) {
            throw new MarketDataUnavailableException("A complete Naver News API result is unavailable.");
        }

        List<StockNewsArticle> analyzed = analyzeAndDeduplicate(snapshot.articles(), query);
        StockNewsResult result = new StockNewsResult(
                stockCode,
                snapshot.query(),
                analyzed,
                snapshot.totalAvailable(),
                analyzed.size(),
                snapshot.dataSource(),
                snapshot.fetchedAt(),
                true,
                freshness(snapshot, analyzed),
                ANALYSIS_METHOD
        );
        return new NewsResponseDto(result);
    }

    private List<StockNewsArticle> analyzeAndDeduplicate(List<StockNewsArticle> rawArticles, String query) {
        Set<String> seenTitles = new HashSet<>();
        return rawArticles.stream()
                .map(article -> analyze(article, query))
                .filter(article -> !article.title().isBlank())
                .filter(article -> seenTitles.add(article.title().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private StockNewsArticle analyze(StockNewsArticle raw, String query) {
        String title = cleanHtml(raw.title());
        String description = cleanHtml(raw.description());
        double sentimentScore = sentimentScore(title + " " + description);
        return new StockNewsArticle(
                title,
                description,
                raw.url(),
                raw.originalUrl(),
                raw.source(),
                raw.publishedAt(),
                qualityScore(title, description, raw),
                relevanceScore(title, description, query),
                sentimentScore,
                sentiment(sentimentScore)
        );
    }

    private String cleanHtml(String text) {
        if (text == null) {
            return "";
        }
        String noHtml = text.replaceAll("<[^>]*>", "");
        return HtmlUtils.htmlUnescape(noHtml).trim();
    }

    private double qualityScore(String title, String description, StockNewsArticle article) {
        double score = 0.0;
        if (!title.isBlank()) score += 0.25;
        if (description.length() >= 40) score += 0.25;
        else if (!description.isBlank()) score += 0.10;
        if (article.originalUrl() != null && !article.originalUrl().isBlank()) score += 0.20;
        if (article.source() != null && !article.source().isBlank() && !"UNKNOWN".equals(article.source())) score += 0.15;
        if (article.publishedAt() != null) score += 0.15;
        return round(score);
    }

    private double relevanceScore(String title, String description, String query) {
        List<String> tokens = Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,()\\[\\]/]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return 0.0;
        }
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        String normalizedDescription = description.toLowerCase(Locale.ROOT);
        long titleMatches = tokens.stream().filter(normalizedTitle::contains).count();
        long descriptionMatches = tokens.stream().filter(normalizedDescription::contains).count();
        double score = 0.7 * titleMatches / tokens.size() + 0.3 * descriptionMatches / tokens.size();
        return round(Math.min(1.0, score));
    }

    private double sentimentScore(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        long positives = POSITIVE_TERMS.stream().filter(normalized::contains).count();
        long negatives = NEGATIVE_TERMS.stream().filter(normalized::contains).count();
        long total = positives + negatives;
        return total == 0 ? 0.0 : round((double) (positives - negatives) / total);
    }

    private NewsSentiment sentiment(double score) {
        if (score > 0.0) return NewsSentiment.POSITIVE;
        if (score < 0.0) return NewsSentiment.NEGATIVE;
        return NewsSentiment.NEUTRAL;
    }

    private String freshness(NewsSearchSnapshot snapshot, List<StockNewsArticle> articles) {
        Instant now = clock.instant();
        Duration threshold = properties.freshnessThreshold() == null
                ? Duration.ofHours(24) : properties.freshnessThreshold();
        if (snapshot.fetchedAt().isBefore(now.minus(threshold))) {
            return "STALE_SOURCE";
        }
        if (articles.isEmpty()) {
            return "EMPTY";
        }
        Instant latest = articles.stream()
                .map(StockNewsArticle::publishedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        if (latest == null) {
            return "UNKNOWN";
        }
        return latest.isBefore(now.minus(threshold)) ? "STALE" : "FRESH";
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
