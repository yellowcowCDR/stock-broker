package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.application.port.out.LoadAgentSkillPerformancePort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPerformancePort;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StrategyPerformancePersistenceAdapter implements SaveAgentSkillPerformancePort, LoadAgentSkillPerformancePort {

    private final AgentSkillPerformanceJpaRepository repository;

    @Override
    public void save(AgentSkillPerformance performance) {
        AgentSkillPerformanceJpaEntity entity = new AgentSkillPerformanceJpaEntity();
        entity.setSkillVersion(performance.skillVersion());
        entity.setTradeCount(performance.tradeCount());
        entity.setEvaluationDays(performance.evaluationDays());
        entity.setWinRate(performance.winRate());
        entity.setTotalReturnRate(performance.totalReturnRate());
        entity.setAverageReturnRate(performance.averageReturnRate());
        entity.setAverageProfit(performance.averageProfit());
        entity.setAverageLoss(performance.averageLoss());
        entity.setProfitLossRatio(performance.profitLossRatio());
        entity.setProfitFactor(performance.profitFactor());
        entity.setSharpeRatio(performance.sharpeRatio());
        entity.setMaxDrawdown(performance.maxDrawdown());
        entity.setHoldAccuracy(performance.holdAccuracy());
        entity.setRiskBlockEffect(performance.riskBlockEffect());
        entity.setEvaluatedAt(performance.evaluatedAt());

        repository.save(entity);
    }

    @Override
    public Optional<AgentSkillPerformance> loadPerformance(String skillVersion) {
        return repository.findById(skillVersion)
                .map(entity -> new AgentSkillPerformance(
                        entity.getSkillVersion(),
                        entity.getTradeCount(),
                        entity.getEvaluationDays(),
                        entity.getWinRate(),
                        entity.getTotalReturnRate(),
                        entity.getAverageReturnRate(),
                        entity.getAverageProfit(),
                        entity.getAverageLoss(),
                        entity.getProfitLossRatio(),
                        entity.getProfitFactor(),
                        entity.getSharpeRatio(),
                        entity.getMaxDrawdown(),
                        entity.getHoldAccuracy(),
                        entity.getRiskBlockEffect(),
                        entity.getEvaluatedAt()
                ));
    }
}
