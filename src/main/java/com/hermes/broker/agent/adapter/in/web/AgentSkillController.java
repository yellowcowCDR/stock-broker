package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.application.port.in.EvolveAgentSkillCommand;
import com.hermes.broker.agent.application.port.in.EvolveAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.GetActiveAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.ManageAgentSkillLifecycleUseCase;
import com.hermes.broker.agent.application.port.in.GetAgentSkillVersionsUseCase;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;

@Tag(name = "Agent Skills API", description = "자율형 에이전트 매매 전략 파라미터 관리 API")
@RestController
@RequestMapping("/api/v1/internal/agent/skills")
@RequiredArgsConstructor
public class AgentSkillController {

    private final GetActiveAgentSkillUseCase getActiveAgentSkillUseCase;
    private final EvolveAgentSkillUseCase evolveAgentSkillUseCase;
    private final ManageAgentSkillLifecycleUseCase manageAgentSkillLifecycleUseCase;
    private final GetAgentSkillVersionsUseCase getAgentSkillVersionsUseCase;

    @Operation(summary = "활성 전략 조회", description = "현재 활성화된 에이전트의 전략 파라미터를 조회합니다.")
    @GetMapping
    public ResponseEntity<AgentSkillResponse> getActiveSkill() {
        AgentSkill activeSkill = getActiveAgentSkillUseCase.getActiveSkill();
        return ResponseEntity.ok(AgentSkillResponse.from(activeSkill));
    }

    @Operation(
            summary = "전략 버전 목록 조회",
            description = "최신 버전부터 반환합니다. status를 생략하면 전체를, CANDIDATE,SHADOW처럼 지정하면 해당 lifecycle만 조회합니다."
    )
    @GetMapping("/versions")
    public ResponseEntity<List<AgentSkillResponse>> getVersions(
            @RequestParam(name = "status", required = false) Set<AgentSkillStatus> statuses) {
        return ResponseEntity.ok(getAgentSkillVersionsUseCase.getVersions(statuses).stream()
                .map(AgentSkillResponse::from)
                .toList());
    }

    @Operation(
            summary = "전략 Candidate 생성(호환 경로)",
            description = "활성 전략은 변경하지 않습니다. activate=true는 거부하며 Shadow 평가와 명시적 승격 승인이 필요합니다."
    )
    @PutMapping
    public ResponseEntity<AgentSkillResponse> evolveSkill(
            @Valid @RequestBody EvolveAgentSkillRequest request) {
        if (Boolean.TRUE.equals(request.activate())) {
            throw new IllegalArgumentException(
                    "Immediate activation is disabled; create a candidate and use shadow evaluation plus promote."
            );
        }
        return createCandidate(request);
    }

    @Operation(summary = "전략 Candidate 생성", description = "새 버전을 CANDIDATE로 저장하며 현재 ACTIVE 전략은 유지합니다.")
    @PostMapping("/candidates")
    public ResponseEntity<AgentSkillResponse> createCandidate(
            @Valid @RequestBody EvolveAgentSkillRequest request) {
        EvolveAgentSkillCommand command = new EvolveAgentSkillCommand(
                request.description(),
                request.skillParameters(),
                request.createdBy()
        );
        AgentSkill evolvedSkill = evolveAgentSkillUseCase.evolve(command);
        return ResponseEntity.ok(AgentSkillResponse.from(evolvedSkill));
    }

    @Operation(summary = "전략 버전 조회")
    @GetMapping("/{version}")
    public ResponseEntity<AgentSkillResponse> getByVersion(@PathVariable int version) {
        return ResponseEntity.ok(AgentSkillResponse.from(
                manageAgentSkillLifecycleUseCase.getByVersion(version)));
    }

    @Operation(summary = "Shadow 실행 시작", description = "CANDIDATE를 SHADOW로 전환하지만 활성 전략은 변경하지 않습니다.")
    @PostMapping("/{version}/shadow/start")
    public ResponseEntity<AgentSkillResponse> startShadow(
            @PathVariable int version,
            @Valid @RequestBody StrategyLifecycleRequest request) {
        return ResponseEntity.ok(AgentSkillResponse.from(
                manageAgentSkillLifecycleUseCase.startShadow(
                        version, request.actor(), request.reason())));
    }

    @Operation(
            summary = "Shadow 성과 평가",
            description = "Broker DB에 계산·저장된 실제 성과만 사용합니다. 성과가 없으면 fail-closed로 거부합니다."
    )
    @PostMapping("/{version}/shadow/evaluate")
    public ResponseEntity<AgentSkillResponse> evaluateShadow(
            @PathVariable int version,
            @Valid @RequestBody StrategyLifecycleRequest request) {
        return ResponseEntity.ok(AgentSkillResponse.from(
                manageAgentSkillLifecycleUseCase.evaluateShadow(
                        version, request.actor(), request.reason())));
    }

    @Operation(summary = "전략 승격", description = "적격 Shadow 평가와 명시적 승인 주체가 있어야 ACTIVE로 전환합니다.")
    @PostMapping("/{version}/promote")
    public ResponseEntity<AgentSkillResponse> promote(
            @PathVariable int version,
            @Valid @RequestBody StrategyLifecycleRequest request) {
        return ResponseEntity.ok(AgentSkillResponse.from(
                manageAgentSkillLifecycleUseCase.promote(
                        version, request.actor(), request.reason())));
    }

    @Operation(summary = "전략 Candidate/Shadow 거절")
    @PostMapping("/{version}/reject")
    public ResponseEntity<AgentSkillResponse> reject(
            @PathVariable int version,
            @Valid @RequestBody StrategyLifecycleRequest request) {
        return ResponseEntity.ok(AgentSkillResponse.from(
                manageAgentSkillLifecycleUseCase.reject(
                        version, request.actor(), request.reason())));
    }
}
