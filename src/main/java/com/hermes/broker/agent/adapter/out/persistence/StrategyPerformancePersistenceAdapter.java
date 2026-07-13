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
        entity.setWinRate(performance.winRate());
        entity.setProfitFactor(performance.profitFactor());
        entity.setMaxDrawdown(performance.maxDrawdown());
        entity.setTotalReturnRate(performance.totalReturnRate());
        entity.setTradeCount(performance.tradeCount());
        entity.setEvaluatedAt(performance.evaluatedAt());

        repository.save(entity);
    }

    @Override
    public Optional<AgentSkillPerformance> loadPerformance(String skillVersion) {
        return repository.findById(skillVersion)
                .map(entity -> new AgentSkillPerformance(
                        entity.getSkillVersion(),
                        entity.getWinRate(),
                        entity.getProfitFactor(),
                        entity.getMaxDrawdown(),
                        entity.getTotalReturnRate(),
                        entity.getTradeCount(),
                        entity.getEvaluatedAt()
                ));
    }
}
