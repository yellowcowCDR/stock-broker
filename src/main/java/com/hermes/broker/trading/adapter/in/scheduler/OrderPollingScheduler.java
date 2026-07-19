package com.hermes.broker.trading.adapter.in.scheduler;

import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.market.adapter.out.external.KisHeaderProvider;
import com.hermes.broker.trading.adapter.out.persistence.TradingLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.common.monitoring.OperationalEventRecorder;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPollingScheduler {

    private final TradingLogJpaRepository tradingLogRepository;
    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final KisRestClientInterceptor kisRestClientInterceptor;
    private final TradingTimeService tradingTimeService;
    private final OperationalEventRecorder operationalEventRecorder;

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
    public void pollPendingOrders() {
        List<TradingLog> pendingLogs = tradingLogRepository.findAll().stream()
                .filter(log -> log.getMarketType() == com.hermes.broker.trading.domain.MarketType.DOMESTIC)
                .filter(log -> log.getExternalOrderId() != null)
                .filter(log -> log.getStatus() == OrderStatus.SUBMITTED
                        || log.getStatus() == OrderStatus.PENDING
                        || log.getStatus() == OrderStatus.PARTIALLY_EXECUTED
                        || log.getStatus() == OrderStatus.CANCEL_REQUESTED)
                .toList();

        if (pendingLogs.isEmpty()) {
            return;
        }

        log.info("Polling {} pending orders...", pendingLogs.size());

        try {
                String trId = kisProperties.environment() == KisEnvironment.MOCK ? "VTTC0081R" : "TTTC0081R";
                String accountNo = kisProperties.api().accountNo();
                String cano = accountNo != null && accountNo.contains("-") ? accountNo.split("-")[0] : accountNo;
                String acntPrdtCd = accountNo != null && accountNo.contains("-") && accountNo.split("-").length > 1 ? accountNo.split("-")[1] : "01";
                
                Map response = inquireDailyCcld(trId, cano, acntPrdtCd,
                        tradingTimeService.currentMarketDate(
                                com.hermes.broker.trading.domain.MarketType.DOMESTIC),
                        "", "", "");

                if (response == null || !"0".equals(response.get("rt_cd")) || !response.containsKey("output1")) {
                    throw new IllegalStateException("KIS execution inquiry failed");
                }

                List<Map<String, String>> output1 = (List<Map<String, String>>) response.get("output1");
                for (TradingLog pendingLog : pendingLogs) {
                    String externalOrderNo = pendingLog.getExternalOrderId().contains("-")
                            ? pendingLog.getExternalOrderId().substring(pendingLog.getExternalOrderId().indexOf('-') + 1)
                            : pendingLog.getExternalOrderId();
                    output1.stream()
                            .filter(row -> externalOrderNo.equals(row.get("odno")))
                            .findFirst()
                            .ifPresent(row -> updateExecutionState(pendingLog, row));
                }
            } catch (Exception e) {
                operationalEventRecorder.recordReconciliation(false,
                        "Domestic execution polling failed: " + e.getMessage());
                log.error("Failed to poll domestic execution status", e);
            }
    }

    /**
     * KIS의 주문별 조회 집계에서 추정 제비용 합계를 별도로 가져옵니다. 이 값은 수수료와
     * 세금을 분리하지 않으므로 Broker도 임의 분할하지 않고 ESTIMATED_COMBINED로 보존합니다.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void reconcileDomesticExecutionCosts() {
        java.time.LocalDate oldestSupportedDate = tradingTimeService.currentMarketDate(
                        com.hermes.broker.trading.domain.MarketType.DOMESTIC)
                .minusDays(89);
        List<TradingLog> candidates = tradingLogRepository
                .findTop5ByMarketTypeAndCostDataCompleteFalseAndStatusInOrderByCreatedAtDesc(
                        com.hermes.broker.trading.domain.MarketType.DOMESTIC,
                        List.of(OrderStatus.EXECUTED, OrderStatus.PARTIALLY_EXECUTED_CANCELED))
                .stream()
                .filter(order -> order.getExternalOrderId() != null)
                .filter(order -> order.getExecutedQuantity() != null
                        && order.getExecutedQuantity().signum() > 0)
                .filter(order -> !order.getCreatedAt()
                        .atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()
                        .isBefore(oldestSupportedDate))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        String trId = kisProperties.environment() == KisEnvironment.MOCK ? "VTTC0081R" : "TTTC0081R";
        String accountNo = kisProperties.api().accountNo();
        String cano = accountNo != null && accountNo.contains("-") ? accountNo.split("-")[0] : accountNo;
        String acntPrdtCd = accountNo != null && accountNo.contains("-")
                && accountNo.split("-").length > 1 ? accountNo.split("-")[1] : "01";

        for (TradingLog order : candidates) {
            try {
                String orderNo = externalOrderNumber(order.getExternalOrderId());
                String orderOffice = externalOrderOffice(order.getExternalOrderId());
                java.time.LocalDate orderDate = order.getCreatedAt()
                        .atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate();
                Map response = inquireDailyCcld(trId, cano, acntPrdtCd, orderDate,
                        order.getStockCode(), orderOffice, orderNo);
                if (response == null || !"0".equals(response.get("rt_cd"))) {
                    throw new IllegalStateException("KIS cost inquiry failed");
                }
                Object output = response.get("output2");
                if (!(output instanceof Map<?, ?> output2)) {
                    throw new IllegalStateException("KIS cost summary is missing");
                }
                BigDecimal combinedCost = requiredDecimal(output2.get("prsm_tlex_smtl"));
                order.reconcileExecutionCost(
                        combinedCost,
                        "KRW",
                        "KIS_INQUIRE_DAILY_CCLD:prsm_tlex_smtl:ESTIMATED_COMBINED",
                        Instant.now());
                tradingLogRepository.save(order);
                operationalEventRecorder.recordReconciliation(true, null);
            } catch (Exception failure) {
                operationalEventRecorder.recordReconciliation(false,
                        "Cost reconciliation failed for broker order " + order.getId()
                                + ": " + failure.getMessage());
                log.warn("KIS execution cost remains incomplete for broker order {}: {}",
                        order.getId(), failure.getMessage());
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private Map inquireDailyCcld(String trId, String cano, String acntPrdtCd,
                                 java.time.LocalDate marketDate, String stockCode,
                                 String orderOffice, String orderNo) {
        String date = marketDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/trading/inquire-daily-ccld")
                        .queryParam("CANO", cano != null ? cano : "")
                        .queryParam("ACNT_PRDT_CD", acntPrdtCd != null ? acntPrdtCd : "01")
                        .queryParam("INQR_STRT_DT", date)
                        .queryParam("INQR_END_DT", date)
                        .queryParam("SLL_BUY_DVSN_CD", "00")
                        .queryParam("INQR_DVSN", "00")
                        .queryParam("PDNO", stockCode)
                        .queryParam("CCLD_DVSN", "00")
                        .queryParam("ORD_GNO_BRNO", orderOffice)
                        .queryParam("ODNO", orderNo)
                        .queryParam("INQR_DVSN_3", "00")
                        .queryParam("INQR_FI_USG_QN", "")
                        .queryParam("CTX_AREA_FK100", "")
                        .queryParam("CTX_AREA_NK100", "")
                        .build())
                .headers(headerProvider.createCommonHeaders(trId))
                .retrieve()
                .body(Map.class);
    }

    private void updateExecutionState(TradingLog order, Map<String, String> row) {
        BigDecimal orderedQuantity = decimal(row.get("ord_qty"));
        BigDecimal executedQuantity = decimal(row.get("tot_ccld_qty"));
        boolean canceled = "Y".equalsIgnoreCase(row.get("cncl_yn"));

        if (executedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            order.updateExecution(decimal(row.get("avg_prvs")), executedQuantity);
        }
        if (executedQuantity.compareTo(orderedQuantity) >= 0) {
            order.updateStatus(OrderStatus.EXECUTED);
        } else if (canceled && executedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            order.updateStatus(OrderStatus.PARTIALLY_EXECUTED_CANCELED);
        } else if (canceled) {
            order.updateStatus(OrderStatus.CANCELED);
            order.reconcileNoExecution("KIS_INQUIRE_DAILY_CCLD:NO_EXECUTION", Instant.now());
        } else if (executedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            order.updateStatus(OrderStatus.PARTIALLY_EXECUTED);
        } else {
            return;
        }
        tradingLogRepository.save(order);
        operationalEventRecorder.recordReconciliation(true, null);
        log.info("Matched KIS execution to broker order {} ({}/{})",
                order.getId(), executedQuantity, orderedQuantity);
    }

    private BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private BigDecimal requiredDecimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("KIS prsm_tlex_smtl is missing");
        }
        BigDecimal parsed = new BigDecimal(value.toString());
        if (parsed.signum() < 0) {
            throw new IllegalStateException("KIS prsm_tlex_smtl is negative");
        }
        return parsed;
    }

    private String externalOrderNumber(String externalOrderId) {
        return externalOrderId.contains("-")
                ? externalOrderId.substring(externalOrderId.indexOf('-') + 1)
                : externalOrderId;
    }

    private String externalOrderOffice(String externalOrderId) {
        return externalOrderId.contains("-")
                ? externalOrderId.substring(0, externalOrderId.indexOf('-'))
                : "";
    }
}
