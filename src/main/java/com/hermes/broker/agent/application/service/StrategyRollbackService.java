package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvaluateStrategyRollbackUseCase;
import com.hermes.broker.agent.application.port.in.RollbackAgentSkillUseCase;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPerformancePort;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.StrategyRollbackEvaluation;
import com.hermes.broker.common.property.StrategyEvaluationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyRollbackService implements EvaluateStrategyRollbackUseCase, RollbackAgentSkillUseCase {

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

        // 이전 버전을 찾아서 성능을 비교해야 함
        // 지금은 단순 목업
        String prevVersion = "v1.0.0-dummy-prev";
        Optional<AgentSkillPerformance> prevPerfOpt = loadAgentSkillPerformancePort.loadPerformance(prevVersion);
        
        if (prevPerfOpt.isPresent()) {
            AgentSkillPerformance prevPerf = prevPerfOpt.get();
            // 손실폭(Drawdown) 증가율, 수익률 저하 등을 검사
            if (currentPerf.maxDrawdown().compareTo(prevPerf.maxDrawdown().add(properties.rollbackMaxDrawdownIncreaseRate())) > 0) {
                return new StrategyRollbackEvaluation(currentVersion, prevVersion, true, "Drawdown increased significantly");
            }
        }
        
        return new StrategyRollbackEvaluation(currentVersion, prevVersion, false, "Performance stable");
    }

    @Override
    public void rollback(String currentVersion, String previousVersion, String reason) {
        log.warn("Rolling back strategy from {} to {}. Reason: {}", currentVersion, previousVersion, reason);
        // 향후 AgentSkill 롤백 및 적용 로직 추가
    }
}
