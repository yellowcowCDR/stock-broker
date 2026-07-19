package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.application.service.AgentResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Tag(name = "Agent Management API", description = "자동 매매 에이전트 초기화 및 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/agent")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "trading.admin-reset", name = "enabled", havingValue = "true")
public class AgentResetController {

    private final AgentResetService agentResetService;

    @Operation(summary = "에이전트 학습 데이터 초기화", description = "에이전트의 이전 매매 로그 및 학습 데이터를 모두 초기화합니다. (테스트/개발용)")
    @PostMapping("/reset")
    public ResponseEntity<String> resetAgentData(@Valid @RequestBody AgentResetRequest request) {
        log.warn("Agent Reset API was called. actor={}, correlationId={}",
                request.actor(), request.correlationId());
        agentResetService.resetLearningData(
                request.actor(), request.reason(), request.correlationId(), request.confirmation());
        return ResponseEntity.ok("Agent memory and logs have been successfully reset.");
    }
}
