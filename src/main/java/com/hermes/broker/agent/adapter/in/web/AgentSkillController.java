package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.application.port.in.EvolveAgentSkillCommand;
import com.hermes.broker.agent.application.port.in.EvolveAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.GetActiveAgentSkillUseCase;
import com.hermes.broker.agent.domain.AgentSkill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Agent Skills API", description = "자율형 에이전트 매매 전략 파라미터 관리 API")
@RestController
@RequestMapping("/api/v1/internal/agent/skills")
@RequiredArgsConstructor
public class AgentSkillController {

    private final GetActiveAgentSkillUseCase getActiveAgentSkillUseCase;
    private final EvolveAgentSkillUseCase evolveAgentSkillUseCase;

    @Operation(summary = "활성 전략 조회", description = "현재 활성화된 에이전트의 전략 파라미터를 조회합니다.")
    @GetMapping
    public ResponseEntity<AgentSkillResponse> getActiveSkill() {
        AgentSkill activeSkill = getActiveAgentSkillUseCase.getActiveSkill();
        return ResponseEntity.ok(AgentSkillResponse.from(activeSkill));
    }

    @Operation(summary = "전략 파라미터 업데이트 및 신규 버전 생성", description = "기존 활성 전략을 비활성화하고 새로운 파라미터가 적용된 새 버전을 생성합니다.")
    @PutMapping
    public ResponseEntity<AgentSkillResponse> evolveSkill(@RequestBody EvolveAgentSkillRequest request) {
        EvolveAgentSkillCommand command = new EvolveAgentSkillCommand(
                request.description(),
                request.skillParameters()
        );
        AgentSkill evolvedSkill = evolveAgentSkillUseCase.evolve(command);
        return ResponseEntity.ok(AgentSkillResponse.from(evolvedSkill));
    }
}
