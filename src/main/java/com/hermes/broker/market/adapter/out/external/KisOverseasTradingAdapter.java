package com.hermes.broker.market.adapter.out.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.CurrentPriceDto;
import com.hermes.broker.market.dto.PortfolioDto;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.OverseasExchange;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor;
import jakarta.annotation.PostConstruct;

import com.hermes.broker.trading.application.port.out.LoadOverseasBalancePort;
import com.hermes.broker.trading.application.port.out.LoadOverseasAccountDataPort;
import com.hermes.broker.trading.application.port.out.SubmitOrderPort;
import com.hermes.broker.trading.application.port.out.LoadOpenOrdersPort;
import com.hermes.broker.trading.application.port.out.CancelOrderPort;
import com.hermes.broker.trading.application.port.out.LoadOrderExecutionsPort;
import com.hermes.broker.trading.domain.portfolio.OverseasBalance;
import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;
import com.hermes.broker.trading.domain.portfolio.OverseasPosition;
import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import com.hermes.broker.trading.domain.OrderExecutionSnapshot;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisOverseasTradingAdapter implements MarketTradingPort, SubmitOrderPort,
        LoadOverseasBalancePort, LoadOverseasAccountDataPort, LoadOpenOrdersPort, CancelOrderPort,
        LoadOrderExecutionsPort {

    private final RestClient.Builder restClientBuilder;
    private final KisHeaderProvider headerProvider;
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

    private String getAccountNumber() {
        return kisProperties.api().accountNo();
    }

    private String getCano() {
        String accountNumber = getAccountNumber();
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        if (accountNumber.contains("-")) {
            return accountNumber.split("-", 2)[0];
        }
        return accountNumber.length() >= 8 ? accountNumber.substring(0, 8) : accountNumber;
    }
    
    private String getAcntPrdtCd() {
        String accountNumber = getAccountNumber();
        if (accountNumber == null || accountNumber.isBlank()) {
            return "01";
        }
        if (accountNumber.contains("-")) {
            String[] parts = accountNumber.split("-", 2);
            return parts.length == 2 && !parts[1].isBlank() ? parts[1] : "01";
        }
        return accountNumber.length() >= 10 ? accountNumber.substring(8, 10) : "01";
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType == MarketType.OVERSEAS;
    }

    @Override
    public CurrentPriceDto getCurrentPrice(String stockCode) {
        return getCurrentPrice(stockCode, OverseasExchange.NASD.orderExchangeCode());
    }

    @Override
    public CurrentPriceDto getCurrentPrice(String stockCode, String exchangeCode) {
        OverseasExchange exchange = OverseasExchange.from(exchangeCode);
        String trId = "HHDFS00000300";

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/price")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", exchange.quoteExchangeCode())
                        .queryParam("SYMB", stockCode)
                        .build())
                .headers(headerProvider.createCommonHeaders(trId))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !"0".equals(response.path("rt_cd").asText())) {
            String message = response == null ? "empty response" : response.path("msg1").asText();
            throw new IllegalStateException("KIS overseas price lookup failed: " + message);
        }
        JsonNode output = response.path("output");
        String currentPriceStr = output.path("last").asText();
        if (currentPriceStr.isBlank()) {
            throw new IllegalStateException("KIS overseas price response is missing last price.");
        }
        BigDecimal currentPrice = new BigDecimal(currentPriceStr);
        if (currentPrice.signum() <= 0) {
            throw new IllegalStateException("KIS overseas price is not positive for " + stockCode + ".");
        }
        return CurrentPriceDto.builder()
                .stockCode(stockCode)
                .currentPrice(currentPrice)
                .build();
    }

    @Override
    public OrderResponseDto placeOrder(OrderRequestDto orderRequest) {
        validateOrderSafety(orderRequest);

        OverseasExchange exchange = OverseasExchange.from(orderRequest.getExchangeCode());
        String trId = overseasOrderTrId(orderRequest.getOrderType());

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("CANO", getCano());
        requestBody.put("ACNT_PRDT_CD", getAcntPrdtCd());
        requestBody.put("OVRS_EXCG_CD", exchange.orderExchangeCode());
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

        if (response == null) {
            throw new IllegalStateException("Empty KIS overseas order response.");
        }

        String rtCd = response.path("rt_cd").asText();
        String msg = response.path("msg1").asText();
        JsonNode output = response.path("output");
        String orderNo = firstText(output, "ODNO", "odno");
        if ("0".equals(rtCd) && orderNo.isBlank()) {
            throw new IllegalStateException("KIS accepted the overseas order but returned no order number.");
        }

        return OrderResponseDto.builder()
                .success("0".equals(rtCd))
                .orderId(orderNo.isBlank() ? null : exchange.orderExchangeCode() + "-" + orderNo)
                .message(msg)
                .build();
    }

    private void validateOrderSafety(OrderRequestDto orderRequest) {
        if (kisProperties.environment() == KisEnvironment.PRODUCTION) {
            throw new IllegalStateException(
                    "Live overseas orders are not supported by this adapter; KIS Paper is required.");
        }
        if (orderRequest.getOrderType() == OrderType.BUY
                && (tradingProperties.killSwitch() == null || tradingProperties.killSwitch().enabled())) {
            throw new IllegalStateException("Entry kill switch is active. New BUY orders are blocked.");
        }
    }

    @Override
    public PortfolioDto getPortfolio() {
        throw new DataPipelineUnavailableException(
                "Overseas portfolio aggregation is not implemented; zero-balance fallback is disabled."
        );
    }

    @Override
    public OverseasBalance loadOverseasBalance() {
        OverseasAccountSnapshot account = loadUnitedStatesAccount();
        return new OverseasBalance(account.cashBalance(), account.availableForUse());
    }

    @Override
    public OverseasAccountSnapshot loadUnitedStatesAccount() {
        ResponseEntity<JsonNode> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-stock/v1/trading/inquire-present-balance")
                        .queryParam("CANO", getCano())
                        .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                        .queryParam("WCRC_FRCR_DVSN_CD", "02")
                        .queryParam("NATN_CD", "840")
                        .queryParam("TR_MKET_CD", "00")
                        .queryParam("INQR_DVSN_CD", "00")
                        .build())
                .headers(headerProvider.createCommonHeaders(
                        resolvedTrId("CTRP6504R", "VTRP6504R")))
                .retrieve()
                .toEntity(JsonNode.class);

        rejectIncompletePage(response, "KIS overseas present-balance");
        return parseUnitedStatesAccount(response.getBody(), clock.instant());
    }

    @Override
    public OverseasOrderCapacity loadOrderCapacity(
            String stockCode,
            String exchangeCode,
            BigDecimal orderPrice
    ) {
        String normalizedExchange = OverseasExchange.from(exchangeCode).orderExchangeCode();
        ResponseEntity<JsonNode> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-stock/v1/trading/inquire-psamount")
                        .queryParam("CANO", getCano())
                        .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                        .queryParam("OVRS_EXCG_CD", normalizedExchange)
                        .queryParam("OVRS_ORD_UNPR", orderPrice.toPlainString())
                        .queryParam("ITEM_CD", stockCode)
                        .build())
                .headers(headerProvider.createCommonHeaders(
                        resolvedTrId("TTTS3007R", "VTTS3007R")))
                .retrieve()
                .toEntity(JsonNode.class);

        rejectIncompletePage(response, "KIS overseas order-capacity");
        return parseOrderCapacity(
                response.getBody(), stockCode, normalizedExchange, orderPrice, clock.instant());
    }

    @Override
    public List<OpenOrder> loadOpenOrders() {
        if (tradingProperties.overseasOrder() == null
                || !tradingProperties.overseasOrder().enabled()) {
            return List.of();
        }
        // KIS defines NASD as the aggregate US-market query for this endpoint.
        return List.copyOf(loadOpenOrders(OverseasExchange.NASD));
    }

    private List<OpenOrder> loadOpenOrders(OverseasExchange exchange) {
        String foreignKey = "";
        String nextKey = "";
        boolean continued = false;
        List<OpenOrder> result = new ArrayList<>();
        for (int page = 0; page < 10; page++) {
            String pageForeignKey = foreignKey;
            String pageNextKey = nextKey;
            boolean continuationRequest = continued;
            ResponseEntity<JsonNode> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/overseas-stock/v1/trading/inquire-nccs")
                            .queryParam("CANO", getCano())
                            .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                            .queryParam("OVRS_EXCG_CD", exchange.orderExchangeCode())
                            .queryParam("SORT_SQN", "DS")
                            .queryParam("CTX_AREA_FK200", pageForeignKey)
                            .queryParam("CTX_AREA_NK200", pageNextKey)
                            .build())
                    .headers(headers -> {
                        headerProvider.createCommonHeaders(
                                resolvedTrId("TTTS3018R", "VTTS3018R")).accept(headers);
                        if (continuationRequest) {
                            headers.set("tr_cont", "N");
                        }
                    })
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = response == null ? null : response.getBody();
            requireSuccess(body, "open-orders");
            for (JsonNode row : asRows(body.path("output"))) {
                OpenOrder order = parseOpenOrder(row, exchange);
                if (order.quantity().compareTo(order.executedQuantity()) > 0) {
                    result.add(order);
                }
            }
            String trCont = response.getHeaders().getFirst("tr_cont");
            if (!"M".equalsIgnoreCase(trCont) && !"F".equalsIgnoreCase(trCont)) {
                return result;
            }
            foreignKey = firstText(body, "ctx_area_fk200", "CTX_AREA_FK200");
            nextKey = firstText(body, "ctx_area_nk200", "CTX_AREA_NK200");
            if (foreignKey.isBlank() && nextKey.isBlank()) {
                throw new DataPipelineUnavailableException(
                        "KIS overseas open-orders indicates another page but returned no context key.");
            }
            continued = true;
        }
        throw new DataPipelineUnavailableException(
                "KIS overseas open-orders exceeded the 10-page safety limit.");
    }

    OpenOrder parseOpenOrder(JsonNode row, OverseasExchange requestedExchange) {
        String exchangeCode = firstText(row, "ovrs_excg_cd", "OVRS_EXCG_CD");
        if (exchangeCode.isBlank()) {
            exchangeCode = requestedExchange.orderExchangeCode();
        }
        exchangeCode = OverseasExchange.from(exchangeCode).orderExchangeCode();
        String orderNo = firstText(row, "odno", "ODNO");
        String stockCode = firstText(row, "pdno", "PDNO");
        if (orderNo.isBlank() || stockCode.isBlank()) {
            throw new DataPipelineUnavailableException(
                    "KIS overseas open-order row is missing order number or symbol.");
        }
        OrderType orderType = parseOrderType(row);
        BigDecimal quantity = requireAnyDecimal(
                row, "overseas ordered quantity", "ft_ord_qty", "ord_qty");
        BigDecimal executed = requireAnyDecimal(
                row, "overseas executed quantity", "ft_ccld_qty", "tot_ccld_qty", "ccld_qty");
        BigDecimal price = requireAnyDecimal(
                row, "overseas order price", "ft_ord_unpr3", "ovrs_ord_unpr", "ord_unpr");
        return new OpenOrder(
                exchangeCode + "-" + orderNo,
                stockCode,
                exchangeCode,
                MarketType.OVERSEAS,
                orderType,
                price,
                quantity,
                executed,
                parseOverseasOrderTime(row)
        );
    }

    private OrderType parseOrderType(JsonNode row) {
        String code = firstText(row, "sll_buy_dvsn_cd", "SLL_BUY_DVSN_CD");
        String name = firstText(
                row, "sll_buy_dvsn_cd_name", "sll_buy_dvsn_name", "sll_buy_dvsn_name1");
        if ("01".equals(code) || name.toUpperCase().contains("SELL") || name.contains("매도")) {
            return OrderType.SELL;
        }
        if ("02".equals(code) || name.toUpperCase().contains("BUY") || name.contains("매수")) {
            return OrderType.BUY;
        }
        throw new DataPipelineUnavailableException("KIS overseas open-order side is missing or unknown.");
    }

    private Instant parseOverseasOrderTime(JsonNode row) {
        String date = firstText(row, "ord_dt", "ord_date");
        String time = firstText(row, "ord_tmd", "ord_time");
        try {
            if (date.isBlank()) {
                date = clock.instant().atZone(java.time.ZoneId.of("America/New_York"))
                        .toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            }
            return java.time.LocalDateTime.of(
                            java.time.LocalDate.parse(date, java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                            java.time.LocalTime.parse(time, java.time.format.DateTimeFormatter.ofPattern("HHmmss")))
                    .atZone(java.time.ZoneId.of("America/New_York"))
                    .toInstant();
        } catch (Exception invalid) {
            throw new DataPipelineUnavailableException("KIS overseas order timestamp is invalid.");
        }
    }

    @Override
    public void cancelOrder(String orderId, String stockCode, MarketType marketType) {
        if (marketType != MarketType.OVERSEAS) {
            throw new UnsupportedOperationException("Overseas adapter cannot cancel " + marketType);
        }
        String[] parts = orderId.split("-", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Overseas order id must include the exchange code.");
        }
        cancelOrder(orderId, stockCode, marketType, parts[0], 0);
    }

    @Override
    public void cancelOrder(
            String orderId,
            String stockCode,
            MarketType marketType,
            String exchangeCode,
            int remainingQuantity
    ) {
        if (marketType != MarketType.OVERSEAS) {
            throw new UnsupportedOperationException("Overseas adapter cannot cancel " + marketType);
        }
        if (kisProperties.environment() == KisEnvironment.PRODUCTION) {
            throw new IllegalStateException("Live overseas cancellation is not enabled.");
        }
        OverseasExchange exchange = OverseasExchange.from(exchangeCode);
        String[] parts = orderId.split("-", 2);
        String originalOrderNo = parts.length == 2 ? parts[1] : orderId;
        if (originalOrderNo.isBlank()) {
            throw new IllegalArgumentException("Invalid overseas KIS order id: " + orderId);
        }
        if (remainingQuantity <= 0) {
            throw new IllegalArgumentException("A positive overseas remaining quantity is required for cancellation.");
        }
        Map<String, String> body = new HashMap<>();
        body.put("CANO", getCano());
        body.put("ACNT_PRDT_CD", getAcntPrdtCd());
        body.put("OVRS_EXCG_CD", exchange.orderExchangeCode());
        body.put("PDNO", stockCode);
        body.put("ORGN_ODNO", originalOrderNo);
        body.put("RVSE_CNCL_DVSN_CD", "02");
        body.put("ORD_QTY", Integer.toString(remainingQuantity));
        body.put("OVRS_ORD_UNPR", "0");
        body.put("MGCO_APTM_ODNO", "");
        body.put("ORD_SVR_DVSN_CD", "0");

        JsonNode response = restClient.post()
                .uri("/uapi/overseas-stock/v1/trading/order-rvsecncl")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headerProvider.createCommonHeaders(
                        resolvedTrId("TTTT1004U", "VTTT1004U")))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        requireSuccess(response, "order-cancel");
    }

    @Override
    public List<OrderExecutionSnapshot> loadOrderExecutions(
            java.time.LocalDate from,
            java.time.LocalDate to
    ) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("A valid overseas execution date range is required.");
        }
        String foreignKey = "";
        String nextKey = "";
        boolean continued = false;
        List<OrderExecutionSnapshot> result = new ArrayList<>();
        for (int page = 0; page < 10; page++) {
            String pageForeignKey = foreignKey;
            String pageNextKey = nextKey;
            boolean continuationRequest = continued;
            ResponseEntity<JsonNode> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/overseas-stock/v1/trading/inquire-ccnl")
                            .queryParam("CANO", getCano())
                            .queryParam("ACNT_PRDT_CD", getAcntPrdtCd())
                            .queryParam("PDNO", kisProperties.environment() == KisEnvironment.MOCK ? "" : "%")
                            .queryParam("ORD_STRT_DT", from.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                            .queryParam("ORD_END_DT", to.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                            .queryParam("SLL_BUY_DVSN", "00")
                            .queryParam("CCLD_NCCS_DVSN", "00")
                            .queryParam("OVRS_EXCG_CD", kisProperties.environment() == KisEnvironment.MOCK ? "" : "%")
                            .queryParam("SORT_SQN", "DS")
                            .queryParam("ORD_DT", "")
                            .queryParam("ORD_GNO_BRNO", "")
                            .queryParam("ODNO", "")
                            .queryParam("CTX_AREA_NK200", pageNextKey)
                            .queryParam("CTX_AREA_FK200", pageForeignKey)
                            .build())
                    .headers(headers -> {
                        headerProvider.createCommonHeaders(
                                resolvedTrId("TTTS3035R", "VTTS3035R")).accept(headers);
                        if (continuationRequest) {
                            headers.set("tr_cont", "N");
                        }
                    })
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = response == null ? null : response.getBody();
            requireSuccess(body, "order-executions");
            for (JsonNode row : asRows(body.path("output"))) {
                result.add(parseOrderExecution(row));
            }
            String trCont = response.getHeaders().getFirst("tr_cont");
            if (!"M".equalsIgnoreCase(trCont) && !"F".equalsIgnoreCase(trCont)) {
                return List.copyOf(result);
            }
            nextKey = firstText(body, "ctx_area_nk200", "CTX_AREA_NK200");
            foreignKey = firstText(body, "ctx_area_fk200", "CTX_AREA_FK200");
            if (foreignKey.isBlank() && nextKey.isBlank()) {
                throw new DataPipelineUnavailableException(
                        "KIS overseas order-executions indicates another page but returned no context key.");
            }
            continued = true;
        }
        throw new DataPipelineUnavailableException(
                "KIS overseas order-executions exceeded the 10-page safety limit.");
    }

    OrderExecutionSnapshot parseOrderExecution(JsonNode row) {
        String orderNo = firstText(row, "odno", "ODNO");
        if (orderNo.isBlank()) {
            throw new DataPipelineUnavailableException("KIS overseas execution row has no order number.");
        }
        BigDecimal ordered = requireAnyDecimal(row, "overseas ordered quantity", "ft_ord_qty", "ord_qty");
        BigDecimal executed = requireAnyDecimal(row, "overseas executed quantity", "ft_ccld_qty", "tot_ccld_qty");
        BigDecimal remaining = findFirstDecimal(row, "nccs_qty");
        if (remaining == null) {
            remaining = ordered.subtract(executed).max(BigDecimal.ZERO);
        }
        BigDecimal executionPrice = findFirstDecimal(row, "ft_ccld_unpr3", "avg_prvs", "ccld_unpr");
        String cancelCode = firstText(row, "rvse_cncl_dvsn");
        String cancelName = firstText(row, "rvse_cncl_dvsn_name", "prcs_stat_name");
        boolean canceled = "02".equals(cancelCode)
                || cancelName.contains("취소")
                || cancelName.toUpperCase().contains("CANCEL");
        String rejectCode = firstText(row, "rjct_rson");
        String rejectName = firstText(row, "rjct_rson_name");
        boolean rejected = !rejectCode.isBlank() && !"0".equals(rejectCode)
                && !"00000000".equals(rejectCode);
        return new OrderExecutionSnapshot(
                orderNo,
                firstText(row, "orgn_odno"),
                firstText(row, "pdno"),
                firstText(row, "ovrs_excg_cd"),
                parseOrderType(row),
                ordered,
                executed,
                executionPrice,
                remaining,
                canceled,
                rejected,
                rejected ? rejectName : firstText(row, "prcs_stat_name"),
                clock.instant()
        );
    }

    OverseasAccountSnapshot parseUnitedStatesAccount(JsonNode response, Instant fetchedAt) {
        requireSuccess(response, "present-balance");

        List<OverseasPosition> positions = new ArrayList<>();
        for (JsonNode row : asRows(response.path("output1"))) {
            String stockCode = firstText(row, "pdno", "std_pdno");
            if (stockCode.isBlank()) {
                throw new DataPipelineUnavailableException(
                        "KIS overseas position is missing pdno/std_pdno."
                );
            }
            positions.add(new OverseasPosition(
                    stockCode,
                    requireText(row, "ovrs_excg_cd", "overseas position exchange"),
                    requireText(row, "buy_crcy_cd", "overseas position currency"),
                    requireDecimal(row, "cblc_qty13", "overseas balance quantity"),
                    requireDecimal(row, "ord_psbl_qty1", "overseas sellable quantity"),
                    requireDecimal(row, "avg_unpr3", "overseas average price"),
                    requireDecimal(row, "ovrs_now_pric1", "overseas current price"),
                    requireDecimal(row, "frcr_evlu_amt2", "overseas evaluation amount"),
                    requireDecimal(row, "evlu_pfls_amt2", "overseas profit/loss amount"),
                    requireDecimal(row, "evlu_pfls_rt1", "overseas profit/loss rate")
                            .movePointLeft(2)
            ));
        }

        JsonNode usdRow = asRows(response.path("output2")).stream()
                .filter(row -> "USD".equalsIgnoreCase(firstText(
                        row, "crcy_cd", "buy_crcy_cd", "tr_crcy_cd")))
                .findFirst()
                .orElseThrow(() -> new DataPipelineUnavailableException(
                        "KIS overseas present-balance response has no USD currency row."
                ));
        BigDecimal cashBalance = requireDecimal(
                usdRow, "frcr_dncl_amt_2", "USD foreign-currency cash balance");

        BigDecimal availableForUse = findDecimal(
                response.path("output3"), "frcr_use_psbl_amt");
        if (availableForUse == null) {
            availableForUse = findDecimal(usdRow, "frcr_use_psbl_amt");
        }
        if (availableForUse == null) {
            throw new DataPipelineUnavailableException(
                    "KIS overseas present-balance response is missing USD available-for-use amount."
            );
        }

        return new OverseasAccountSnapshot(
                "840",
                "USD",
                cashBalance,
                availableForUse,
                positions,
                "KIS_OPEN_API:INQUIRE_PRESENT_BALANCE:"
                        + resolvedTrId("CTRP6504R", "VTRP6504R"),
                fetchedAt,
                true
        );
    }

    OverseasOrderCapacity parseOrderCapacity(
            JsonNode response,
            String stockCode,
            String exchangeCode,
            BigDecimal requestedPrice,
            Instant fetchedAt
    ) {
        requireSuccess(response, "order-capacity");
        JsonNode output = response.path("output");
        if (output.isArray()) {
            if (output.isEmpty()) {
                throw new DataPipelineUnavailableException(
                        "KIS overseas order-capacity output is empty."
                );
            }
            output = output.get(0);
        }

        String currency = requireText(output, "tr_crcy_cd", "order-capacity currency");
        if (!"USD".equalsIgnoreCase(currency)) {
            throw new DataPipelineUnavailableException(
                    "Expected USD order capacity but KIS returned " + currency + "."
            );
        }
        return new OverseasOrderCapacity(
                stockCode,
                exchangeCode,
                currency,
                requestedPrice,
                requireDecimal(output, "ord_psbl_frcr_amt", "orderable foreign amount"),
                requireDecimal(output, "ovrs_ord_psbl_amt", "overseas orderable amount"),
                requireDecimal(output, "max_ord_psbl_qty", "maximum orderable quantity"),
                requireDecimal(output, "ord_psbl_qty", "orderable quantity"),
                requireDecimal(output, "exrt", "order-capacity exchange rate"),
                "KIS_OPEN_API:INQUIRE_PSAMOUNT:"
                        + resolvedTrId("TTTS3007R", "VTTS3007R"),
                fetchedAt,
                true
        );
    }

    private void rejectIncompletePage(ResponseEntity<JsonNode> response, String operation) {
        if (response == null || response.getBody() == null) {
            throw new DataPipelineUnavailableException(operation + " returned an empty response.");
        }
        String continuation = response.getHeaders().getFirst("tr_cont");
        if ("M".equalsIgnoreCase(continuation) || "F".equalsIgnoreCase(continuation)) {
            throw new DataPipelineUnavailableException(
                    operation + " requires pagination; partial account data was not returned."
            );
        }
    }

    private void requireSuccess(JsonNode response, String operation) {
        if (response == null || !"0".equals(response.path("rt_cd").asText())) {
            String message = response == null
                    ? "empty response" : response.path("msg1").asText("unknown error");
            throw new DataPipelineUnavailableException(
                    "KIS overseas " + operation + " lookup failed: " + message
            );
        }
    }

    private List<JsonNode> asRows(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<JsonNode> rows = new ArrayList<>();
            node.forEach(rows::add);
            return rows;
        }
        return List.of(node);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String requireText(JsonNode node, String field, String label) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new DataPipelineUnavailableException("KIS response is missing " + label + ".");
        }
        return value;
    }

    private BigDecimal requireDecimal(JsonNode node, String field, String label) {
        BigDecimal value = findDecimal(node, field);
        if (value == null) {
            throw new DataPipelineUnavailableException("KIS response is missing " + label + ".");
        }
        return value;
    }

    private BigDecimal requireAnyDecimal(JsonNode node, String label, String... fields) {
        for (String field : fields) {
            BigDecimal value = findDecimal(node, field);
            if (value != null) {
                return value;
            }
        }
        throw new DataPipelineUnavailableException("KIS response is missing " + label + ".");
    }

    private BigDecimal findFirstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = findDecimal(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal findDecimal(JsonNode node, String field) {
        for (JsonNode row : asRows(node)) {
            String raw = row.path(field).asText("").trim().replace(",", "");
            if (!raw.isBlank()) {
                try {
                    return new BigDecimal(raw);
                } catch (NumberFormatException invalid) {
                    throw new DataPipelineUnavailableException(
                            "KIS response contains invalid numeric field " + field + "."
                    );
                }
            }
        }
        return null;
    }

    private String resolvedTrId(String productionTrId, String mockTrId) {
        return kisProperties.environment() == KisEnvironment.MOCK
                ? mockTrId : productionTrId;
    }

    String overseasOrderTrId(OrderType orderType) {
        if (kisProperties.environment() == KisEnvironment.MOCK) {
            return orderType == OrderType.BUY ? "VTTT1002U" : "VTTT1001U";
        }
        return orderType == OrderType.BUY ? "TTTT1002U" : "TTTT1006U";
    }
}
