package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvaluateStrategyPerformanceUseCase;
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
public class StrategyPerformanceService implements EvaluateStrategyPerformanceUseCase {

    private final SaveAgentSkillPerformancePort saveAgentSkillPerformancePort;

    @Override
    public AgentSkillPerformance evaluate(String strategyVersion) {
        log.info("Evaluating performance for strategy version: {}", strategyVersion);
        
        // (실제로는 LoadTradingDecisionPort, LoadTradingReflectionPort 등을 통해 과거 데이터를 집계)
        
        AgentSkillPerformance performance = new AgentSkillPerformance(
                strategyVersion,
                new BigDecimal("0.55"), // winRate
                new BigDecimal("1.2"),  // profitFactor
                new BigDecimal("0.15"), // maxDrawdown
                new BigDecimal("0.08"), // totalReturnRate
                50,                     // tradeCount
                LocalDateTime.now()
        );
        
        saveAgentSkillPerformancePort.save(performance);
        return performance;
    }
}
