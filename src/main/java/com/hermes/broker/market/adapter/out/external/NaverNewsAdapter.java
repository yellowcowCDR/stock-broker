package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.ExternalApiNotConfiguredException;
import com.hermes.broker.common.exception.NaverNewsApiException;
import com.hermes.broker.common.property.NaverNewsProperties;
import com.hermes.broker.market.application.port.out.NaverNewsSearchPort;
import com.hermes.broker.market.domain.NewsSearchSnapshot;
import com.hermes.broker.market.domain.NewsSentiment;
import com.hermes.broker.market.domain.StockNewsArticle;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewsAdapter implements NaverNewsSearchPort {

    private final NaverNewsProperties properties;
    private final Clock clock;
    private RestClient restClient;

    @PostConstruct
    public void init() {
        if (!properties.enabled() || properties.clientId() == null || properties.clientId().isBlank()) {
            log.warn("Naver News API is not configured or disabled.");
            return;
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Naver-Client-Id", properties.clientId())
                .defaultHeader("X-Naver-Client-Secret", properties.clientSecret())
                .build();
    }

    @Override
    @Cacheable(value = "naver_news", key = "#keyword + ':' + #display")
    @SuppressWarnings("unchecked")
    public NewsSearchSnapshot searchNewsByKeyword(String keyword, int display) {
        if (restClient == null) {
            throw new ExternalApiNotConfiguredException("Naver News API is not configured.");
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/news.json")
                            .queryParam("query", keyword)
                            .queryParam("display", Math.min(display, properties.maxDisplay()))
                            .queryParam("sort", "date")
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("items") instanceof List<?>)) {
                throw new NaverNewsApiException("Naver News API returned an invalid response.");
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            Instant fetchedAt = clock.instant();
            List<StockNewsArticle> articles = items.stream()
                    .filter(Objects::nonNull)
                    .map(this::toArticle)
                    .toList();

            return new NewsSearchSnapshot(
                    keyword,
                    articles,
                    requiredLong(response.get("total"), "total"),
                    fetchedAt,
                    "NAVER_SEARCH_NEWS_API",
                    true
            );

        } catch (NaverNewsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Naver News API error for keyword: {}", keyword, e);
            throw new NaverNewsApiException("Failed to fetch news from Naver API", e);
        }
    }

    private StockNewsArticle toArticle(Map<String, Object> item) {
        String title = text(item.get("title"));
        String link = text(item.get("link"));
        String originalLink = text(item.get("originallink"));
        if (title.isBlank() || (link.isBlank() && originalLink.isBlank())) {
            throw new NaverNewsApiException("Naver News API article is missing title or URL.");
        }
        String source = sourceOf(originalLink.isBlank() ? link : originalLink);
        if ("UNKNOWN".equals(source)) {
            throw new NaverNewsApiException("Naver News API article URL is invalid.");
        }
        return new StockNewsArticle(
                title,
                text(item.get("description")),
                link,
                originalLink,
                source,
                parseDate(text(item.get("pubDate"))),
                0.0,
                0.0,
                0.0,
                NewsSentiment.NEUTRAL
        );
    }

    private Instant parseDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            throw new NaverNewsApiException("Naver News API article is missing pubDate.");
        }
        try {
            return ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            throw new NaverNewsApiException("Naver News API pubDate is invalid: " + pubDate, e);
        }
    }

    private static String sourceOf(String url) {
        if (url == null || url.isBlank()) {
            return "UNKNOWN";
        }
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? "UNKNOWN" : host;
        } catch (IllegalArgumentException ignored) {
            return "UNKNOWN";
        }
    }

    private static long requiredLong(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException e) {
            throw new NaverNewsApiException("Naver News API field is missing or invalid: " + field, e);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
