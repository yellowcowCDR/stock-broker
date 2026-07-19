package com.hermes.broker.trading.application.service;

import com.hermes.broker.market.application.port.in.MarketIntelligenceUseCase;
import com.hermes.broker.market.application.port.out.LoadMarketContextPort;
import com.hermes.broker.market.domain.MarketContext;
import com.hermes.broker.market.dto.response.IntelligenceResponseDto;
import com.hermes.broker.trading.application.port.in.AgentTradingUseCase;
import com.hermes.broker.trading.application.port.in.RunTradingCycleUseCase;
import com.hermes.broker.trading.application.port.in.SaveTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.SaveTradingFeatureUseCase;
import com.hermes.broker.trading.application.port.out.InvokeAgentDecisionPort;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.decision.TradingCycleResult;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingCycleService implements RunTradingCycleUseCase {

    private final MarketIntelligenceUseCase marketIntelligenceUseCase;
    private final SaveTradingFeatureUseCase saveTradingFeatureUseCase;
    private final LoadMarketContextPort loadMarketContextPort;
    private final InvokeAgentDecisionPort invokeAgentDecisionPort;
    private final SaveTradingDecisionUseCase saveTradingDecisionUseCase;
    private final AgentTradingUseCase agentTradingUseCase;

    @Override
    public TradingCycleResult runForStock(String stockCode) {
        log.info("Starting trading cycle for stock: {}", stockCode);
        
        try {
            IntelligenceResponseDto intelligenceDto = marketIntelligenceUseCase.getIntelligence(stockCode);
            
            Map<String, Object> techFeatures = new HashMap<>();
            Map<String, Object> newsFeatures = new HashMap<>();
            Map<String, Object> riskFeatures = new HashMap<>();
            
            if (intelligenceDto != null && intelligenceDto.intelligence() != null) {
                techFeatures.put("profile", intelligenceDto.intelligence().profile());
                newsFeatures.put("news", intelligenceDto.intelligence().newsAnalysis());
            }

            loadMarketContextPort.loadLatest(com.hermes.broker.trading.domain.MarketType.DOMESTIC)
                    .ifPresent(context -> addMarketContextAudit(riskFeatures, context));

            // 2. Snapshot 생성 및 저장
            TradingFeatureSnapshot snapshot = new TradingFeatureSnapshot(
                    UUID.randomUUID().toString(),
                    stockCode,
                    com.hermes.broker.trading.domain.MarketType.DOMESTIC,
                    techFeatures,
                    newsFeatures,
                    riskFeatures,
                    Instant.now()
            );
            saveTradingFeatureUseCase.save(snapshot);

            // 3. Broker에는 내장 매매 판단기가 없으므로 기본 어댑터는 BLOCK을 반환한다.
            TradingDecision initialDecision = invokeAgentDecisionPort.invoke(snapshot);
            saveTradingDecisionUseCase.save(initialDecision);

            // 판단이 HOLD나 BLOCK이면 바로 종료
            if (initialDecision.decisionType() == TradingDecisionType.HOLD || initialDecision.decisionType() == TradingDecisionType.BLOCK) {
                return new TradingCycleResult(stockCode, true, "Decision is " + initialDecision.decisionType(), initialDecision, Instant.now());
            }

            // 4. 주문 실행. Risk/환경/시장/idempotency 검증은 공통 주문 파이프라인이 전담한다.
            OrderType orderType = initialDecision.decisionType() == TradingDecisionType.BUY ? OrderType.BUY : OrderType.SELL;
            OrderRequestDto orderReq = OrderRequestDto.builder()
                    .marketType(com.hermes.broker.trading.domain.MarketType.DOMESTIC)
                    .stockCode(stockCode)
                    .orderType(orderType)
                    .price(initialDecision.recommendedPrice())
                    .quantity(initialDecision.recommendedQuantity().intValue())
                    .idempotencyKey("trading-cycle:" + initialDecision.decisionId())
                    .decisionId(initialDecision.decisionId())
                    .featureId(initialDecision.featureId())
                    .strategyVersion(initialDecision.strategyVersion())
                    .decisionReason(initialDecision.reason())
                    .build();

            OrderResponseDto orderResp = agentTradingUseCase.placeOrder(orderReq);
            if (orderResp.isSuccess()) {
                log.info("Order placed successfully for {}", stockCode);
            } else {
                log.warn("Order blocked or failed for {}: {}", stockCode, orderResp.getMessage());
            }
            return new TradingCycleResult(stockCode, true,
                    orderResp.isSuccess() ? "Cycle completed" : "Order blocked: " + orderResp.getMessage(),
                    initialDecision, Instant.now());

        } catch (Exception e) {
            log.error("Failed to run trading cycle for {}", stockCode, e);
            return new TradingCycleResult(stockCode, false, e.getMessage(), null, Instant.now());
        }
    }

    private void addMarketContextAudit(Map<String, Object> riskFeatures, MarketContext context) {
        riskFeatures.put("marketContextId", context.contextId());
        riskFeatures.put("marketContextAnalyzedAt", context.analyzedAt());
        riskFeatures.put("marketContextValidUntil", context.validUntil());
        riskFeatures.put("marketEntryPolicy", context.entryPolicy().name());
        riskFeatures.put("marketRiskMultiplier", context.riskMultiplier());
        riskFeatures.put("marketOverviewDataSource", context.overviewSnapshot().dataSource());
        riskFeatures.put("marketOverviewFetchedAt", context.overviewSnapshot().fetchedAt());
        riskFeatures.put("marketOverviewComplete", context.overviewSnapshot().complete());
    }
}
