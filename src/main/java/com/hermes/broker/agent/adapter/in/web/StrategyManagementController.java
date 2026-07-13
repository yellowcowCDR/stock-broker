package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.application.port.in.EvaluateStrategyPerformanceUseCase;
import com.hermes.broker.agent.application.port.in.EvaluateStrategyRollbackUseCase;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.StrategyRollbackEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/broker/agent/strategy")
@RequiredArgsConstructor
public class StrategyManagementController {

    private final EvaluateStrategyPerformanceUseCase evaluateStrategyPerformanceUseCase;
    private final EvaluateStrategyRollbackUseCase evaluateStrategyRollbackUseCase;

    @PostMapping("/evaluate/{version}")
    public ResponseEntity<AgentSkillPerformance> evaluatePerformance(@PathVariable String version) {
        return ResponseEntity.ok(evaluateStrategyPerformanceUseCase.evaluate(version));
    }

    @GetMapping("/rollback-check/{version}")
    public ResponseEntity<StrategyRollbackEvaluation> checkRollback(@PathVariable String version) {
        return ResponseEntity.ok(evaluateStrategyRollbackUseCase.evaluateRollback(version));
    }
}
