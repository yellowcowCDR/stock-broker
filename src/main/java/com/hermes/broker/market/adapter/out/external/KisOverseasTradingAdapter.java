package com.hermes.broker.market.adapter.out.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.market.dto.PortfolioDto;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisOverseasTradingAdapter implements MarketTradingPort {

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;
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

    private String getAccountNumber() {
        return kisProperties.api().accountNo();
    }

    private String getCano() {
        String accountNumber = getAccountNumber();
        return accountNumber != null && accountNumber.length() >= 8 ? accountNumber.substring(0, 8) : "";
    }
    
    private String getAcntPrdtCd() {
        String accountNumber = getAccountNumber();
        return accountNumber != null && accountNumber.length() >= 10 ? accountNumber.substring(8, 10) : "01";
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType == MarketType.OVERSEAS;
    }

    @Override
    public CurrentPriceDto getCurrentPrice(String stockCode) {
        String trId = "HHDFS76200200";
        
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/price-detail")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", "NAS")
                        .queryParam("SYMB", stockCode)
                        .build())
                .headers(headerProvider.createCommonHeaders(trId))
                .retrieve()
                .body(JsonNode.class);

        log.debug("Overseas price API response: {}", response);
        
        JsonNode output = response.path("output");
        String currentPriceStr = output.path("last").asText("0"); 
        
        if (currentPriceStr.isEmpty()) {
            currentPriceStr = "0";
        }
        
        return CurrentPriceDto.builder()
                .stockCode(stockCode)
                .currentPrice(new BigDecimal(currentPriceStr))
                .build();
    }

    @Override
    public OrderResponseDto placeOrder(OrderRequestDto orderRequest) {
        validateOrderSafety();
        
        String trId = orderRequest.getOrderType() == OrderType.BUY ? "JTTT1002U" : "JTTT1006U";
        
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("CANO", getCano());
        requestBody.put("ACNT_PRDT_CD", getAcntPrdtCd());
        requestBody.put("OVRS_EXCG_CD", "NASD"); 
        requestBody.put("PDNO", orderRequest.getStockCode());
        requestBody.put("ORD_DVSN", "00"); 
        requestBody.put("ORD_QTY", String.valueOf(orderRequest.getQuantity()));
        requestBody.put("OVRS_ORD_UNPR", orderRequest.getPrice().toPlainString());
        requestBody.put("ORD_SVR_DVSN_CD", "0");

        JsonNode response = restClient.post()
                .uri("/uapi/overseas-stock/v1/trading/order")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headerProvider.createCommonHeaders(trId))
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        log.info("Overseas order API response: {}", response);

        String rtCd = response.path("rt_cd").asText();
        String msg = response.path("msg1").asText();
        JsonNode output = response.path("output");
        String orderNo = output.path("ODNO").asText("");

        return OrderResponseDto.builder()
                .success("0".equals(rtCd))
                .orderId(orderNo)
                .message(msg)
                .build();
    }

    private void validateOrderSafety() {
        if (kisProperties.environment() == KisEnvironment.PRODUCTION) {
            boolean realOrderEnabled = tradingProperties.realOrder() != null && tradingProperties.realOrder().enabled();
            boolean killSwitchEnabled = tradingProperties.killSwitch() == null || tradingProperties.killSwitch().enabled();

            if (!realOrderEnabled) {
                throw new IllegalStateException("Real orders are disabled in configuration.");
            }
            if (killSwitchEnabled) {
                throw new IllegalStateException("Kill switch is active. Real orders are blocked.");
            }
        }
    }

    @Override
    public PortfolioDto getPortfolio() {
        String trId = "CTRP6504R"; 
        
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-stock/v1/trading/inquire-present-balance")
                        .queryParam("CANO", getCano())
                        .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                        .queryParam("WCRC_FRCR_DVSN_CD", "01") 
                        .queryParam("NATN_CD", "840") 
                        .queryParam("TR_MKET_CD", "00") 
                        .build())
                .headers(headerProvider.createCommonHeaders(trId))
                .retrieve()
                .body(JsonNode.class);

        log.debug("Overseas balance API response: {}", response);
        
        return PortfolioDto.builder()
                .totalAsset(BigDecimal.ZERO)
                .availableCash(BigDecimal.ZERO)
                .holdings(java.util.Collections.emptyList())
                .build();
    }
}
