package com.hermes.broker.trading.application.service;

import com.hermes.broker.market.application.port.in.MarketIntelligenceUseCase;
import com.hermes.broker.market.application.port.out.MarketTradingPort;
import com.hermes.broker.market.dto.response.IntelligenceResponseDto;
import com.hermes.broker.trading.application.port.in.EvaluateOrderRiskUseCase;
import com.hermes.broker.trading.application.port.in.OrderRiskCommand;
import com.hermes.broker.trading.application.port.in.RunTradingCycleUseCase;
import com.hermes.broker.trading.application.port.in.SaveTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.SaveTradingFeatureUseCase;
import com.hermes.broker.trading.application.port.out.InvokeAgentDecisionPort;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.decision.TradingCycleResult;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import com.hermes.broker.trading.domain.risk.RiskDecision;
import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;
import com.hermes.broker.trading.dto.OrderRequestDto;
import com.hermes.broker.trading.dto.OrderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingCycleService implements RunTradingCycleUseCase {

    private final MarketIntelligenceUseCase marketIntelligenceUseCase;
    private final SaveTradingFeatureUseCase saveTradingFeatureUseCase;
    private final InvokeAgentDecisionPort invokeAgentDecisionPort;
    private final EvaluateOrderRiskUseCase evaluateOrderRiskUseCase;
    private final SaveTradingDecisionUseCase saveTradingDecisionUseCase;
    private final MarketTradingPort marketTradingPort;

    @Override
    public TradingCycleResult runForStock(String stockCode) {
        log.info("Starting trading cycle for stock: {}", stockCode);
        
        try {
            IntelligenceResponseDto intelligenceDto = marketIntelligenceUseCase.getIntelligence(stockCode);
            
            Map<String, Object> techFeatures = new HashMap<>();
            Map<String, Object> newsFeatures = new HashMap<>();
            
            if (intelligenceDto != null && intelligenceDto.intelligence() != null) {
                techFeatures.put("profile", intelligenceDto.intelligence().profile());
                newsFeatures.put("news", intelligenceDto.intelligence().newsAnalysis());
            }

            // 2. Snapshot 생성 및 저장
            TradingFeatureSnapshot snapshot = new TradingFeatureSnapshot(
                    UUID.randomUUID().toString(),
                    stockCode,
                    techFeatures,
                    newsFeatures,
                    new HashMap<>(), // Risk Features can be added here
                    LocalDateTime.now()
            );
            saveTradingFeatureUseCase.save(snapshot);

            // 3. Agent 판단 (임시 더미 에이전트 구현이 포트 어댑터 쪽에 필요)
            TradingDecision initialDecision = invokeAgentDecisionPort.invoke(snapshot);

            // 판단이 HOLD나 BLOCK이면 바로 종료
            if (initialDecision.decisionType() == TradingDecisionType.HOLD || initialDecision.decisionType() == TradingDecisionType.BLOCK) {
                saveTradingDecisionUseCase.save(initialDecision);
                return new TradingCycleResult(stockCode, true, "Decision is " + initialDecision.decisionType(), initialDecision, LocalDateTime.now());
            }

            // 4. 리스크 검증 (BUY 또는 SELL)
            OrderType orderType = initialDecision.decisionType() == TradingDecisionType.BUY ? OrderType.BUY : OrderType.SELL;
            OrderRiskCommand riskCommand = new OrderRiskCommand(
                    stockCode,
                    com.hermes.broker.trading.domain.MarketType.DOMESTIC,
                    orderType,
                    initialDecision.recommendedPrice(),
                    initialDecision.recommendedQuantity(),
                    "UNKNOWN"
            );
            
            RiskEvaluationResult riskResult = evaluateOrderRiskUseCase.evaluate(riskCommand);
            
            TradingDecision finalDecision;
            if (riskResult.allowed()) {
                finalDecision = initialDecision;
                
                // 5. 주문 실행
                OrderRequestDto orderReq = OrderRequestDto.builder()
                        .stockCode(stockCode)
                        .orderType(orderType)
                        .price(finalDecision.recommendedPrice())
                        .quantity(finalDecision.recommendedQuantity().intValue())
                        .build();
                        
                OrderResponseDto orderResp = marketTradingPort.placeOrder(orderReq);
                if (orderResp.isSuccess()) {
                    log.info("Order placed successfully for {}", stockCode);
                } else {
                    log.warn("Order failed for {}: {}", stockCode, orderResp.getMessage());
                    // 실제 환경에선 부분 체결/실패에 대한 상태를 업데이트해야 하지만, 현재 Decision만 업데이트
                }
            } else {
                // 리스크에서 차단된 경우, BLOCK으로 덮어씀
                finalDecision = new TradingDecision(
                        initialDecision.decisionId(),
                        initialDecision.featureId(),
                        initialDecision.stockCode(),
                        TradingDecisionType.BLOCK,
                        initialDecision.strategyVersion(),
                        String.join(", ", riskResult.reasons()),
                        initialDecision.recommendedPrice(),
                        initialDecision.recommendedQuantity(),
                        LocalDateTime.now()
                );
            }

            // 6. 최종 판단 결과 저장
            saveTradingDecisionUseCase.save(finalDecision);
            
            return new TradingCycleResult(stockCode, true, "Cycle completed", finalDecision, LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to run trading cycle for {}", stockCode, e);
            return new TradingCycleResult(stockCode, false, e.getMessage(), null, LocalDateTime.now());
        }
    }
}
