package com.hermes.broker.trading.adapter.in.scheduler;

import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.market.adapter.out.external.KisHeaderProvider;
import com.hermes.broker.trading.adapter.out.persistence.TradingLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPollingScheduler {

    private final TradingLogJpaRepository tradingLogRepository;
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

    /**
     * 10초마다 PENDING 상태인 주문을 찾아 KIS API로 체결 여부를 확인하고 업데이트합니다.
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void pollPendingOrders() {
        // 실제로는 PENDING인 것만 가져오는 쿼리 메서드가 필요합니다.
        List<TradingLog> pendingLogs = tradingLogRepository.findAll().stream()
                .filter(log -> log.getStatus() == OrderStatus.PENDING)
                .toList();

        if (pendingLogs.isEmpty()) {
            return;
        }

        log.info("Polling {} pending orders...", pendingLogs.size());

        for (TradingLog pendingLog : pendingLogs) {
            try {
                // KIS 주식일별주문체결조회 (TTTC8001R) 등 실제 체결확인 API 연동 부분
                // 여기서는 예시로 KIS API 응답을 받아 체결 처리했다고 가정하는 로직을 작성합니다.
                
                String trId = "TTTC8001R"; // 주식일별주문체결조회
                String accountNo = kisProperties.api().accountNo();
                String cano = accountNo != null && accountNo.contains("-") ? accountNo.split("-")[0] : accountNo;
                String acntPrdtCd = accountNo != null && accountNo.contains("-") && accountNo.split("-").length > 1 ? accountNo.split("-")[1] : "01";
                
                Map response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/uapi/domestic-stock/v1/trading/inquire-daily-ccld")
                                .queryParam("CANO", cano != null ? cano : "")
                                .queryParam("ACNT_PRDT_CD", acntPrdtCd != null ? acntPrdtCd : "01")
                                .queryParam("INQR_STRT_DT", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")))
                                .queryParam("INQR_END_DT", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")))
                                .queryParam("SLL_BUY_DVSN_CD", "00")
                                .queryParam("INQR_DVSN", "00")
                                .queryParam("PDNO", "")
                                .queryParam("CCLD_DVSN", "01") // 01: 체결
                                .queryParam("ORD_GNO_BRNO", "")
                                .queryParam("ODNO", "")
                                .queryParam("INQR_DVSN_3", "00")
                                .queryParam("INQR_FI_USG_QN", "")
                                .queryParam("CTX_AREA_FK100", "")
                                .queryParam("CTX_AREA_NK100", "")
                                .build())
                        .headers(headerProvider.createCommonHeaders(trId))
                        .retrieve()
                        .body(Map.class);

                // 응답에서 해당 종목이 체결되었는지 판단
                // 실제 프로덕션에서는 원주문번호(ODNO) 등을 비교하여 매칭해야 합니다.
                boolean isExecuted = response != null && response.containsKey("output1") && !((List)response.get("output1")).isEmpty();

                if (isExecuted) {
                    // 체결 단가 파싱 (실제 output1 내 체결가 추출)
                    List<Map<String, String>> output1 = (List<Map<String, String>>) response.get("output1");
                    BigDecimal execPrice = new BigDecimal(output1.get(0).get("avg_prvs")); 
                    
                    pendingLog.updateExecutionPrice(execPrice);
                    pendingLog.updateStatus(OrderStatus.EXECUTED);
                    tradingLogRepository.save(pendingLog);
                    log.info("Order for {} executed at {}", pendingLog.getStockCode(), execPrice);
                }

            } catch (Exception e) {
                log.error("Failed to poll execution status for log ID: {}", pendingLog.getId(), e);
            }
        }
    }
}
