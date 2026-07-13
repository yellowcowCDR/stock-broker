package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvaluateStrategyPerformanceUseCase;
import com.hermes.broker.agent.application.port.in.GetStrategyPerformanceUseCase;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPerformancePort;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyPerformanceService implements EvaluateStrategyPerformanceUseCase, GetStrategyPerformanceUseCase {

    private final SaveAgentSkillPerformancePort saveAgentSkillPerformancePort;

    @Override
    public AgentSkillPerformance evaluate(String strategyVersion) {
        log.info("Evaluating performance for strategy version: {}", strategyVersion);
        
        // (실제로는 LoadTradingDecisionPort, LoadTradingReflectionPort 등을 통해 과거 데이터를 집계)
        
        AgentSkillPerformance performance = new AgentSkillPerformance(
                strategyVersion,
                50,                     // tradeCount
                10,                     // evaluationDays
                new BigDecimal("0.55"), // winRate
                new BigDecimal("0.08"), // totalReturnRate
                new BigDecimal("0.01"), // averageReturnRate
                new BigDecimal("0.05"), // averageProfit
                new BigDecimal("-0.03"),// averageLoss
                new BigDecimal("1.6"),  // profitLossRatio
                new BigDecimal("1.2"),  // profitFactor
                new BigDecimal("1.5"),  // sharpeRatio
                new BigDecimal("0.15"), // maxDrawdown
                new BigDecimal("0.8"),  // holdAccuracy
                new BigDecimal("0.9"),  // riskBlockEffect
                LocalDateTime.now()
        );
        
        saveAgentSkillPerformancePort.save(performance);
        return performance;
    }

    @Override
    public AgentSkillPerformance getPerformance(String version) {
        // Return latest evaluated performance
        // In real impl, fetch from DB. Returning a dummy for now.
        return new AgentSkillPerformance(
                version,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDateTime.now()
        );
    }
}
