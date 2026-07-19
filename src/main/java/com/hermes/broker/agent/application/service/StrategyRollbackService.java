package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvaluateStrategyRollbackUseCase;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPerformancePort;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.StrategyRollbackEvaluation;
import com.hermes.broker.common.property.StrategyEvaluationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyRollbackService implements EvaluateStrategyRollbackUseCase {

    private final StrategyEvaluationProperties properties;
    private final LoadAgentSkillPerformancePort loadAgentSkillPerformancePort;

    @Override
    public StrategyRollbackEvaluation evaluateRollback(String currentVersion) {
        if (!properties.rollbackEnabled()) {
            return new StrategyRollbackEvaluation(currentVersion, null, false, "Rollback disabled");
        }

        Optional<AgentSkillPerformance> currentPerfOpt = loadAgentSkillPerformancePort.loadPerformance(currentVersion);
        if (currentPerfOpt.isEmpty()) {
            return new StrategyRollbackEvaluation(currentVersion, null, false, "No performance data");
        }
        
        AgentSkillPerformance currentPerf = currentPerfOpt.get();
        if (currentPerf.tradeCount() < properties.rollbackMinimumSample()) {
            return new StrategyRollbackEvaluation(currentVersion, null, false, "Not enough trade samples");
        }

        return new StrategyRollbackEvaluation(
                currentVersion,
                null,
                false,
                "Previous active strategy version is not available from a real version-history pipeline"
        );
    }

}
