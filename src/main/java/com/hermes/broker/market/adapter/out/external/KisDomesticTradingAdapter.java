package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import com.hermes.broker.market.dto.PortfolioDto;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.hermes.broker.trading.application.port.out.LoadAccountBalancePort;
import com.hermes.broker.trading.application.port.out.LoadBuyingPowerPort;
import com.hermes.broker.trading.application.port.out.LoadOpenOrdersPort;
import com.hermes.broker.trading.application.port.out.LoadPortfolioPositionsPort;
import com.hermes.broker.trading.application.port.out.CancelOrderPort;
import com.hermes.broker.trading.application.port.out.SubmitOrderPort;
import com.hermes.broker.trading.domain.portfolio.AccountBalance;
import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class KisDomesticTradingAdapter implements MarketTradingPort, SubmitOrderPort, LoadAccountBalancePort, LoadBuyingPowerPort, LoadPortfolioPositionsPort, LoadOpenOrdersPort, CancelOrderPort {

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;

    @Override
    public boolean supports(MarketType marketType) {
        return marketType == MarketType.DOMESTIC;
    }

    private final MarketTimeValidator timeValidator;
    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;
    private final KisRestClientInterceptor kisRestClientInterceptor;
    private final Clock clock;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        String baseUrl = kisProperties.baseUrl();
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestInterceptor(kisRestClientInterceptor)
                .build();
    }

    private String getAccountNo() {
        return kisProperties.api().accountNo();
    }

    private String getCano() {
        String accountNo = getAccountNo();
        if (accountNo == null) return "";
        if (accountNo.contains("-")) {
            return accountNo.split("-")[0];
        }
        if (accountNo.length() >= 10) {
            return accountNo.substring(0, 8);
        }
        return accountNo;
    }

    private String getAcntPrdtCd() {
        String accountNo = getAccountNo();
        if (accountNo == null) return "01";
        if (accountNo.contains("-")) {
            String[] parts = accountNo.split("-");
            return parts.length > 1 ? parts[1] : "01";
        }
        if (accountNo.length() >= 10) {
            return accountNo.substring(8, 10);
        }
        return "01";
    }

    @Override
    public CurrentPriceDto getCurrentPrice(String stockCode) {
        String trId = "FHKST01010100"; // 국내주식 기본시세
        
        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J") // J: 주식, ETF, ETN
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .build())
                    .headers(headerProvider.createCommonHeaders(trId))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"0".equals(response.get("rt_cd")) || !response.containsKey("output")) {
                String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
                throw new IllegalStateException("Failed to get current price for " + stockCode + ": " + message);
            }

            Map<String, String> output = (Map<String, String>) response.get("output");
            
            // KIS API 응답(난해한 필드명) -> 공통 DTO 매핑
            return CurrentPriceDto.builder()
                    .stockCode(stockCode)
                    .currentPrice(new BigDecimal(output.get("stck_prpr"))) // 주식 현재가
                    .changeRate(new BigDecimal(output.get("prdy_ctrt")))   // 전일 대비율
                    .accumulatedVolume(Long.parseLong(output.get("acml_vol"))) // 누적 거래량
                    .build();

        } catch (Exception e) {
            log.error("Error occurred while fetching current price for {}", stockCode, e);
            throw new RuntimeException("Current price fetch failed", e);
        }
    }

    @Override
    public OrderResponseDto placeOrder(OrderRequestDto orderRequest) {
        timeValidator.validateMarketOpen("DOMESTIC"); // 장 운영 시간 검증
        validateOrderSafety(orderRequest); // 어댑터 직전 최종 안전 검증
        
        String trId;
        if (kisProperties.environment() == KisEnvironment.MOCK) {
            trId = orderRequest.getOrderType() == OrderType.BUY ? "VTTC0802U" : "VTTC0801U";
        } else {
            trId = orderRequest.getOrderType() == OrderType.BUY ? "TTTC0802U" : "TTTC0801U";
        }

        Map<String, Object> body = Map.of(
                "CANO", getCano(), // 종합계좌번호(앞 8자리)
                "ACNT_PRDT_CD", getAcntPrdtCd(), // 계좌상품코드(뒤 2자리)
                "PDNO", orderRequest.getStockCode(),
                "ORD_DVSN", "00", // 00: 지정가 (시장가 등은 별도 처리 필요)
                "ORD_QTY", String.valueOf(orderRequest.getQuantity()),
                "ORD_UNPR", orderRequest.getPrice().toPlainString()
        );

        try {
            Map response = restClient.post()
                    .uri("/uapi/domestic-stock/v1/trading/order-cash")
                    .headers(headerProvider.createCommonHeaders(trId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException("Empty KIS order response");
            }
            boolean success = "0".equals(response.get("rt_cd"));
            String msg = (String) response.get("msg1");

            String orderId = null;
            if (success && response.containsKey("output")) {
                Map<String, String> output = (Map<String, String>) response.get("output");
                orderId = output.get("KRX_FWDG_ORD_ORGNO") + "-" + output.get("ODNO"); // 주문번호 조합
            }

            return OrderResponseDto.builder()
                    .success(success)
                    .orderId(orderId)
                    .message(msg)
                    .build();

        } catch (Exception e) {
            log.error("Order failed for {}", orderRequest.getStockCode(), e);
            throw new RuntimeException("KIS order result could not be confirmed", e);
        }
    }

    private void validateOrderSafety(OrderRequestDto orderRequest) {
        validateOrderAccess();
        if (orderRequest.getOrderType() == OrderType.BUY
                && (tradingProperties.killSwitch() == null || tradingProperties.killSwitch().enabled())) {
            throw new IllegalStateException("Entry kill switch is active. New BUY orders are blocked.");
        }
    }

    private void validateOrderAccess() {
        if (kisProperties.environment() == KisEnvironment.PRODUCTION) {
            boolean realOrderEnabled = tradingProperties.realOrder() != null && tradingProperties.realOrder().enabled();

            if (!realOrderEnabled) {
                throw new IllegalStateException("Real orders are disabled in configuration.");
            }
        }
    }

    @Override
    public PortfolioDto getPortfolio() {
        String trId = kisProperties.environment() == KisEnvironment.MOCK ? "VTTC8434R" : "TTTC8434R";

        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/trading/inquire-balance")
                            .queryParam("CANO", getCano())
                            .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                            .queryParam("AFHR_FLPR_YN", "N")
                            .queryParam("OFL_YN", "")
                            .queryParam("INQR_DVSN", "02")
                            .queryParam("UNPR_DVSN", "01")
                            .queryParam("FUND_STTL_ICLD_YN", "N")
                            .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                            .queryParam("PRCS_DVSN", "00")
                            .queryParam("CTX_AREA_FK100", "")
                            .queryParam("CTX_AREA_NK100", "")
                            .build())
                    .headers(headerProvider.createCommonHeaders(trId))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"0".equals(response.get("rt_cd"))
                    || !(response.get("output1") instanceof List<?>)
                    || !(response.get("output2") instanceof List<?>)) {
                String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
                throw new IllegalStateException("Failed to get KIS portfolio: " + message);
            }

            List<Map<String, String>> output1 = (List<Map<String, String>>) response.get("output1"); // 개별 종목 잔고
            List<Map<String, String>> output2 = (List<Map<String, String>>) response.get("output2"); // 계좌 요약
            
            if (output2.isEmpty()) {
                throw new IllegalStateException("KIS portfolio response has no account summary.");
            }
            Map<String, String> accountSummary = output2.get(0);
            DailyAssetChangeData dailyAssetChange = toDailyAssetChangeData(accountSummary);

            List<PortfolioDto.StockHolding> holdings = output1.stream()
                    .map(item -> PortfolioDto.StockHolding.builder()
                            .stockCode(item.get("pdno"))
                            .stockName(item.get("prdt_name"))
                            .quantity(Integer.parseInt(item.get("hldg_qty")))
                            .averageBuyPrice(new BigDecimal(item.get("pchs_avg_pric")))
                            .currentPrice(new BigDecimal(item.get("prpr")))
                            .returnRate(new BigDecimal(item.get("evlu_pfls_rt")))
                            .build())
                    .toList();

            return PortfolioDto.builder()
                    .totalAsset(new BigDecimal(accountSummary.get("tot_evlu_amt"))) // 총 평가 금액
                    .availableCash(new BigDecimal(accountSummary.get("dnca_tot_amt"))) // 예수금 총액
                    .totalEvaluationAmount(requiredDecimal(accountSummary, "evlu_amt_smtl_amt"))
                    .totalProfitLossAmount(requiredDecimal(accountSummary, "evlu_pfls_smtl_amt"))
                    .previousTotalAssetAmount(dailyAssetChange.previousTotalAssetAmount())
                    .dailyAssetChangeAmount(dailyAssetChange.amount())
                    .dailyAssetChangeRate(dailyAssetChange.rate())
                    .dailyAssetChangeDataComplete(dailyAssetChange.complete())
                    .dailyAssetChangeDataSource(dailyAssetChange.dataSource())
                    .holdings(holdings)
                    .build();

        } catch (Exception e) {
            log.error("Error occurred while fetching portfolio", e);
            throw new RuntimeException("Portfolio fetch failed", e);
        }
    }

    @Override
    public AccountBalance loadBalance() {
        PortfolioDto domestic = getPortfolio(); // domestic portfolio API
        return new AccountBalance(
                domestic.getTotalAsset(),
                domestic.getAvailableCash(),
                domestic.getTotalEvaluationAmount(),
                domestic.getTotalProfitLossAmount(),
                domestic.getPreviousTotalAssetAmount(),
                domestic.getDailyAssetChangeAmount(),
                domestic.getDailyAssetChangeRate(),
                domestic.isDailyAssetChangeDataComplete(),
                domestic.getDailyAssetChangeDataSource(),
                null,
                null
        );
    }



    @Override
    public BigDecimal loadBuyingPower() {
        String trId = kisProperties.environment() == KisEnvironment.MOCK ? "VTTC8908R" : "TTTC8908R";
        
        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/trading/inquire-psbl-order")
                            .queryParam("CANO", getCano())
                            .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                            .queryParam("PDNO", "")
                            .queryParam("ORD_UNPR", "")
                            .queryParam("ORD_DVSN", "00")
                            .queryParam("CMA_EVLU_AMT_ICLD_YN", "N")
                            .queryParam("OVRS_ICLD_YN", "N")
                            .build())
                    .headers(headerProvider.createCommonHeaders(trId))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"0".equals(response.get("rt_cd")) || !response.containsKey("output")) {
                String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
                throw new IllegalStateException("KIS domestic buying-power lookup failed: " + message);
            }

            Map<String, String> output = (Map<String, String>) response.get("output");
            String buyingPowerStr = output.get("nrcvb_buy_amt"); // 미수 없는 실제 매수 가능 금액
            if (buyingPowerStr == null || buyingPowerStr.isEmpty()) {
                throw new IllegalStateException("KIS domestic buying-power amount is missing.");
            }
            
            return new BigDecimal(buyingPowerStr);

        } catch (Exception e) {
            log.error("Error occurred while fetching domestic buying power", e);
            throw new IllegalStateException("Domestic buying-power lookup failed.", e);
        }
    }

    @Override
    public List<PortfolioPosition> loadPositions() {
        String trId = kisProperties.environment() == KisEnvironment.MOCK ? "VTTC8434R" : "TTTC8434R";
        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/trading/inquire-balance")
                            .queryParam("CANO", getCano())
                            .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                            .queryParam("AFHR_FLPR_YN", "N")
                            .queryParam("OFL_YN", "")
                            .queryParam("INQR_DVSN", "02")
                            .queryParam("UNPR_DVSN", "01")
                            .queryParam("FUND_STTL_ICLD_YN", "N")
                            .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                            .queryParam("PRCS_DVSN", "00")
                            .queryParam("CTX_AREA_FK100", "")
                            .queryParam("CTX_AREA_NK100", "")
                            .build())
                    .headers(headerProvider.createCommonHeaders(trId))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"0".equals(response.get("rt_cd"))
                    || !(response.get("output1") instanceof List<?>)) {
                String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
                throw new IllegalStateException("KIS positions lookup failed: " + message);
            }

            List<Map<String, String>> output1 = (List<Map<String, String>>) response.get("output1");
            return output1.stream()
                    .map(item -> new PortfolioPosition(
                            item.get("pdno"),
                            item.get("prdt_name"),
                            MarketType.DOMESTIC,
                            "UNKNOWN", // 업종(단순조회에서는 미제공)
                            new BigDecimal(item.get("hldg_qty")), // 보유수량
                            new BigDecimal(item.get("ord_psbl_qty")), // 주문가능수량
                            new BigDecimal(item.get("pchs_avg_pric")),
                            new BigDecimal(item.get("prpr")),
                            new BigDecimal(item.get("evlu_amt")), // 평가금액
                            new BigDecimal(item.get("evlu_pfls_amt")), // 평가손익금액
                            new BigDecimal(item.get("evlu_pfls_rt")), // 평가손익율
                            BigDecimal.ZERO // 비중은 Service에서 다시 계산됨
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("loadPositions error", e);
            throw new IllegalStateException("Domestic positions lookup failed.", e);
        }
    }

    @Override
    public List<OpenOrder> loadOpenOrders() {
        ZonedDateTime seoulNow = ZonedDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        String date = seoulNow.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String trId = kisProperties.environment() == KisEnvironment.MOCK ? "VTTC0081R" : "TTTC0081R";

        Map response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/trading/inquire-daily-ccld")
                        .queryParam("CANO", getCano())
                        .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                        .queryParam("INQR_STRT_DT", date)
                        .queryParam("INQR_END_DT", date)
                        .queryParam("SLL_BUY_DVSN_CD", "00")
                        .queryParam("INQR_DVSN", "00")
                        .queryParam("PDNO", "")
                        .queryParam("CCLD_DVSN", "02")
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

        if (response == null || !"0".equals(response.get("rt_cd")) || !response.containsKey("output1")) {
            String message = response == null ? "empty response" : String.valueOf(response.get("msg1"));
            throw new IllegalStateException("Unable to verify KIS open orders: " + message);
        }

        List<Map<String, String>> rows = (List<Map<String, String>>) response.get("output1");
        return rows.stream()
                .map(this::toOpenOrder)
                .filter(order -> order.quantity().compareTo(order.executedQuantity()) > 0)
                .toList();
    }

    private OpenOrder toOpenOrder(Map<String, String> row) {
        String office = requiredFirstNonBlank(row.get("ord_gno_brno"), row.get("krx_fwdg_ord_orgno"), "order office");
        String orderNo = requiredFirstNonBlank(row.get("odno"), row.get("ODNO"), "order number");
        BigDecimal quantity = requiredDecimal(row, "ord_qty");
        BigDecimal executedQuantity = requiredDecimal(row, "tot_ccld_qty");
        OrderType orderType = "01".equals(row.get("sll_buy_dvsn_cd")) ? OrderType.SELL : OrderType.BUY;
        return new OpenOrder(
                office + "-" + orderNo,
                row.get("pdno"),
                MarketType.DOMESTIC,
                orderType,
                requiredDecimal(row, "ord_unpr"),
                quantity,
                executedQuantity,
                parseOrderedAt(row.get("ord_dt"), row.get("ord_tmd"))
        );
    }

    private Instant parseOrderedAt(String date, String time) {
        try {
            LocalDate parsedDate = LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
            LocalTime parsedTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HHmmss"));
            return LocalDateTime.of(parsedDate, parsedTime)
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toInstant();
        } catch (Exception ignored) {
            throw new IllegalStateException("KIS order timestamp is invalid: " + date + " " + time);
        }
    }

    private BigDecimal requiredDecimal(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("KIS response is missing numeric field: " + key);
        }
        return new BigDecimal(value.replace(",", ""));
    }

    static DailyAssetChangeData toDailyAssetChangeData(Map<String, String> accountSummary) {
        BigDecimal previousTotalAsset = nullableDecimal(accountSummary, "bfdy_tot_asst_evlu_amt");
        BigDecimal amount = nullableDecimal(accountSummary, "asst_icdc_amt");
        BigDecimal percent = nullableDecimal(accountSummary, "asst_icdc_erng_rt");
        boolean complete = previousTotalAsset != null && amount != null && percent != null;
        return new DailyAssetChangeData(
                previousTotalAsset,
                amount,
                percent == null ? null : percent.movePointLeft(2),
                complete,
                complete ? "KIS_OPEN_API:INQUIRE_BALANCE:ASST_ICDC" : null
        );
    }

    private static BigDecimal nullableDecimal(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("KIS response field is not numeric: " + key, e);
        }
    }

    private String requiredFirstNonBlank(String first, String second, String field) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        throw new IllegalStateException("KIS response is missing " + field + ".");
    }

    record DailyAssetChangeData(
            BigDecimal previousTotalAssetAmount,
            BigDecimal amount,
            BigDecimal rate,
            boolean complete,
            String dataSource
    ) {
    }

    @Override
    public void cancelOrder(String orderId, String stockCode, MarketType marketType) {
        if (marketType != MarketType.DOMESTIC) {
            throw new UnsupportedOperationException("Domestic adapter cannot cancel " + marketType + " orders");
        }
        String[] parts = orderId.split("-", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid domestic KIS order id: " + orderId);
        }

        String trId = kisProperties.environment() == KisEnvironment.MOCK ? "VTTC0803U" : "TTTC0803U";
        Map<String, Object> body = Map.of(
                "CANO", getCano(),
                "ACNT_PRDT_CD", getAcntPrdtCd(),
                "KRX_FWDG_ORD_ORGNO", parts[0],
                "ORGN_ODNO", parts[1],
                "ORD_DVSN", "00",
                "RVSE_CNCL_DVSN_CD", "02",
                "ORD_QTY", "0",
                "ORD_UNPR", "0",
                "QTY_ALL_ORD_YN", "Y"
        );

        Map response = restClient.post()
                .uri("/uapi/domestic-stock/v1/trading/order-rvsecncl")
                .headers(headerProvider.createCommonHeaders(trId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null || !"0".equals(response.get("rt_cd"))) {
            String message = response == null ? "Empty KIS cancellation response" : String.valueOf(response.get("msg1"));
            throw new IllegalStateException("KIS cancellation rejected: " + message);
        }
    }
}
