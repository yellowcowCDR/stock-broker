package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.ExternalApiNotConfiguredException;
import com.hermes.broker.common.exception.NaverNewsApiException;
import com.hermes.broker.common.property.NaverNewsProperties;
import com.hermes.broker.market.application.port.out.NaverNewsSearchPort;
import com.hermes.broker.market.domain.StockNewsArticle;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewsAdapter implements NaverNewsSearchPort {

    private final NaverNewsProperties properties;
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
    @Cacheable(value = "naver_news", key = "#keyword")
    public List<StockNewsArticle> searchNewsByKeyword(String keyword, int display) {
        if (restClient == null) {
            throw new ExternalApiNotConfiguredException("Naver News API is not configured.");
        }

        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/news.json")
                            .queryParam("query", keyword)
                            .queryParam("display", Math.min(display, properties.maxDisplay()))
                            .queryParam("sort", "date")
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("items")) {
                return Collections.emptyList();
            }

            List<Map<String, String>> items = (List<Map<String, String>>) response.get("items");

            return items.stream()
                    .map(item -> new StockNewsArticle(
                            item.get("title"),
                            item.get("description"),
                            item.get("link"), // originallink 도 있지만 link(네이버뉴스) 사용
                            parseDate(item.get("pubDate")),
                            0.0 // 초기 점수
                    ))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Naver News API error for keyword: {}", keyword, e);
            throw new NaverNewsApiException("Failed to fetch news from Naver API", e);
        }
    }

    private LocalDateTime parseDate(String pubDate) {
        if (pubDate == null) return LocalDateTime.now();
        try {
            // EEE, dd MMM yyyy HH:mm:ss Z (e.g. Thu, 13 Jul 2026 09:15:21 +0900)
            return LocalDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse pubDate: {}", pubDate);
            return LocalDateTime.now();
        }
    }
}
