package com.hermes.broker.market.application.service;

import com.hermes.broker.market.adapter.out.external.KisHeaderProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.hermes.broker.market.dto.response.MarketStatusResponseDto;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTimeValidator {

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final KisRestClientInterceptor kisRestClientInterceptor;
    
    private RestClient restClient;

    @PostConstruct
    public void init() {
        String baseUrl = kisProperties.baseUrl();
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestInterceptor(kisRestClientInterceptor)
                .build();
    }

    public String getEnvironmentName() {
        return kisProperties.environment().name();
    }

    /**
     * KIS API를 연동하여 현재 국내 주식 장이 개장된 상태인지 확인합니다.
     * 캐시를 적용하여 30분 단위로만 실제 API를 호출합니다.
     */
    @Cacheable(value = "marketStatusCache", key = "#root.target.getEnvironmentName() + ':domestic'")
    public boolean isDomesticMarketOpen() {
        String baseUrl = kisProperties.baseUrl();
        if (baseUrl.contains("openapivts")) {
            log.info("Mock environment detected. Skipping KIS holiday API check.");
            LocalTime now = LocalTime.now();
            // UTC 기준 00:00 ~ 06:30 (KST 09:00 ~ 15:30)
            LocalTime open = LocalTime.of(0, 0);
            LocalTime close = LocalTime.of(6, 30);
            DayOfWeek day = LocalDate.now().getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                return false;
            }
            return !now.isBefore(open) && !now.isAfter(close);
        }

        log.info("Fetching market status from KIS API (Cache Miss)");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String trId = "CTCA0903R"; // 국내휴장일조회 (또는 사용하는 KIS 휴장일 API TR ID)

        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/chk-holiday")
                            .queryParam("BASS_DT", today)
                            .queryParam("CTX_AREA_NK", "")
                            .queryParam("CTX_AREA_FK", "")
                            .build())
                    .headers(headerProvider.createCommonHeaders(trId))
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("output")) {
                List<Map<String, String>> output = (List<Map<String, String>>) response.get("output");
                if (!output.isEmpty()) {
                    // 영업일 여부 판단 (opnd_yn: 개장여부, bzdy_yn: 영업일여부)
                    String isBusinessDay = output.get(0).get("bzdy_yn");
                    // 영업일이 아니면 false 반환
                    if (!"Y".equalsIgnoreCase(isBusinessDay)) {
                        return false;
                    }
                    
                    // 영업일이라면 현재 시간이 UTC 기준 00:00 ~ 06:30 (KST 09:00 ~ 15:30) 사이인지 추가 확인
                    LocalTime now = LocalTime.now();
                    LocalTime open = LocalTime.of(0, 0);
                    LocalTime close = LocalTime.of(6, 30);
                    return !now.isBefore(open) && !now.isAfter(close);
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to fetch market open status", e);
            // API 오류 시 기본적으로 false로 막을지, 일단 통과시킬지 정책 결정 필요. 여기서는 안전하게 예외 전파.
            throw new RuntimeException("Cannot verify market open status", e);
        }
    }

    public void validateMarketOpen(String marketType) {
        MarketStatusResponseDto status = getMarketStatus(marketType);
        if (!status.isOpen()) {
            throw new IllegalStateException("Market (" + marketType + ") is closed. Status: " + status.getStatus());
        }
    }

    public MarketStatusResponseDto getMarketStatus(String marketType) {
        boolean open = false;
        String status = "CLOSED";
        
        try {
            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
            
            if ("OVERSEAS".equalsIgnoreCase(marketType)) {
                // Convert UTC to NY time to safely handle US DST automatically
                ZonedDateTime nyTime = nowUtc.withZoneSameInstant(ZoneId.of("America/New_York"));
                LocalTime time = nyTime.toLocalTime();
                DayOfWeek day = nyTime.getDayOfWeek();
                
                // TODO: Integrate actual KIS overseas holiday API here if needed.
                // For now, simple weekend check.
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    status = "CLOSED";
                    open = false;
                } else {
                    if (!time.isBefore(LocalTime.of(4, 0)) && time.isBefore(LocalTime.of(9, 30))) {
                        status = "PRE_MARKET";
                        open = true; // or false depending on whether you allow pre-market trading
                    } else if (!time.isBefore(LocalTime.of(9, 30)) && time.isBefore(LocalTime.of(16, 0))) {
                        status = "REGULAR_MARKET";
                        open = true;
                    } else if (!time.isBefore(LocalTime.of(16, 0)) && time.isBefore(LocalTime.of(20, 0))) {
                        status = "AFTER_MARKET";
                        open = true; // or false depending on whether you allow after-market trading
                    } else if (time.isAfter(LocalTime.of(20, 0)) || time.isBefore(LocalTime.of(4, 0))) {
                        status = "DAY_TRADING";
                        open = true; // Blue Ocean day trading hours roughly
                    } else {
                        status = "CLOSED";
                        open = false;
                    }
                }
            } else {
                // DOMESTIC logic
                open = isDomesticMarketOpen();
                status = open ? "REGULAR_MARKET" : "CLOSED";
            }
        } catch (Exception e) {
            log.error("Error checking market status", e);
            status = "ERROR";
        }

        return MarketStatusResponseDto.builder()
                .marketType(marketType)
                .isOpen(open)
                .status(status)
                .checkedAt(ZonedDateTime.now(ZoneId.of("UTC")))
                .build();
    }
}
