package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.application.port.in.EvaluateStrategyPerformanceUseCase;
import com.hermes.broker.agent.application.port.in.EvaluateStrategyRollbackUseCase;
import com.hermes.broker.agent.application.port.in.GetStrategyPerformanceUseCase;
import com.hermes.broker.agent.application.port.in.RollbackAgentSkillUseCase;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.StrategyRollbackEvaluation;
import com.hermes.broker.agent.dto.RollbackRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/agent/skills")
@RequiredArgsConstructor
public class StrategyManagementController {

    private final EvaluateStrategyPerformanceUseCase evaluateStrategyPerformanceUseCase;
    private final GetStrategyPerformanceUseCase getStrategyPerformanceUseCase;
    private final EvaluateStrategyRollbackUseCase evaluateStrategyRollbackUseCase;
    private final RollbackAgentSkillUseCase rollbackAgentSkillUseCase;

    @PostMapping("/{version}/performance/evaluate")
    public ResponseEntity<AgentSkillPerformance> evaluatePerformance(@PathVariable String version) {
        return ResponseEntity.ok(evaluateStrategyPerformanceUseCase.evaluate(version));
    }

    @GetMapping("/{version}/performance")
    public ResponseEntity<AgentSkillPerformance> getPerformance(@PathVariable String version) {
        return ResponseEntity.ok(getStrategyPerformanceUseCase.getPerformance(version));
    }

    @GetMapping("/{version}/rollback-evaluation")
    public ResponseEntity<StrategyRollbackEvaluation> checkRollback(@PathVariable String version) {
        return ResponseEntity.ok(evaluateStrategyRollbackUseCase.evaluateRollback(version));
    }

    @PostMapping("/rollback")
    public ResponseEntity<Void> rollback(@RequestBody RollbackRequestDto request) {
        rollbackAgentSkillUseCase.rollback(request.targetVersion(), null, request.reason());
        return ResponseEntity.ok().build();
    }
}
