package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import com.hermes.broker.market.dto.PortfolioDto;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.market.adapter.out.external.KisHeaderProvider;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.application.service.MarketTimeValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.hermes.broker.trading.application.port.out.LoadAccountBalancePort;
import com.hermes.broker.trading.application.port.out.LoadBuyingPowerPort;
import com.hermes.broker.trading.application.port.out.LoadOpenOrdersPort;
import com.hermes.broker.trading.application.port.out.LoadPortfolioPositionsPort;
import com.hermes.broker.trading.application.port.out.CancelOrderPort;
import com.hermes.broker.trading.domain.portfolio.AccountBalance;
import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class KisDomesticTradingAdapter implements MarketTradingPort, LoadAccountBalancePort, LoadBuyingPowerPort, LoadPortfolioPositionsPort, LoadOpenOrdersPort, CancelOrderPort {

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;

    @Override
    public boolean supports(MarketType marketType) {
        return marketType == MarketType.DOMESTIC;
    }

    private final MarketTimeValidator timeValidator;

    @Value("${kis.api.base-url}")
    private String baseUrl;

    @Value("${kis.api.account-no}")
    private String accountNo;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
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

            if (response == null || !response.containsKey("output")) {
                throw new IllegalStateException("Failed to get current price for " + stockCode);
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
        timeValidator.validateMarketOpen(); // 장 운영 시간 검증
        // 매수: TTTC0802U, 매도: TTTC0801U
        String trId = orderRequest.getOrderType() == OrderType.BUY ? "TTTC0802U" : "TTTC0801U";

        Map<String, Object> body = Map.of(
                "CANO", accountNo.split("-")[0], // 종합계좌번호(앞 8자리)
                "ACNT_PRDT_CD", accountNo.split("-")[1], // 계좌상품코드(뒤 2자리)
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
            return OrderResponseDto.builder()
                    .success(false)
                    .message("Exception occurred: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PortfolioDto getPortfolio() {
        String trId = "TTTC8434R"; // 주식 잔고조회

        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/trading/inquire-balance")
                            .queryParam("CANO", accountNo.split("-")[0])
                            .queryParam("ACNT_PRDT_CD", accountNo.split("-")[1])
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

            if (response == null || !response.containsKey("output2")) {
                throw new IllegalStateException("Failed to get portfolio");
            }

            List<Map<String, String>> output1 = (List<Map<String, String>>) response.get("output1"); // 개별 종목 잔고
            List<Map<String, String>> output2 = (List<Map<String, String>>) response.get("output2"); // 계좌 요약
            
            Map<String, String> accountSummary = output2.get(0);

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
                    .holdings(holdings)
                    .build();

        } catch (Exception e) {
            log.error("Error occurred while fetching portfolio", e);
            throw new RuntimeException("Portfolio fetch failed", e);
        }
    }

    @Override
    public AccountBalance loadBalance() {
        String trId = "TTTC8434R"; // 주식 잔고조회
        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/trading/inquire-balance")
                            .queryParam("CANO", accountNo.split("-")[0])
                            .queryParam("ACNT_PRDT_CD", accountNo.split("-")[1])
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

            if (response == null || !response.containsKey("output2")) {
                throw new IllegalStateException("Failed to get balance");
            }
            List<Map<String, String>> output2 = (List<Map<String, String>>) response.get("output2");
            Map<String, String> accountSummary = output2.get(0);
            
            BigDecimal totalAsset = new BigDecimal(accountSummary.get("tot_evlu_amt"));
            BigDecimal cash = new BigDecimal(accountSummary.get("dnca_tot_amt")); // 예수금총액
            BigDecimal evalAmt = new BigDecimal(accountSummary.get("scts_evlu_amt")); // 유가증권평가금액
            BigDecimal pnl = new BigDecimal(accountSummary.get("evlu_pfls_smtl_amt")); // 평가손익합계금액

            return new AccountBalance(totalAsset, cash, evalAmt, pnl);
        } catch (Exception e) {
            log.error("loadBalance error", e);
            return new AccountBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    @Override
    public BigDecimal loadBuyingPower() {
        // 간이 구현: 예수금 반환
        return loadBalance().cashAmount();
    }

    @Override
    public List<PortfolioPosition> loadPositions() {
        String trId = "TTTC8434R"; 
        try {
            Map response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/trading/inquire-balance")
                            .queryParam("CANO", accountNo.split("-")[0])
                            .queryParam("ACNT_PRDT_CD", accountNo.split("-")[1])
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

            if (response == null || !response.containsKey("output1")) {
                return Collections.emptyList();
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
            return Collections.emptyList();
        }
    }

    @Override
    public List<OpenOrder> loadOpenOrders() {
        log.info("[KIS Domestic] Loading open orders...");
        return Collections.emptyList();
    }

    @Override
    public void cancelOrder(String orderId, String stockCode, MarketType marketType) {
        log.info("[KIS Domestic] Canceling order... orderId={}, stockCode={}", orderId, stockCode);
        // 실제 KIS 한국투자증권 API 취소 호출 구현
    }
}
