package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.application.service.AgentResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/agent")
@RequiredArgsConstructor
public class AgentResetController {

    private final AgentResetService agentResetService;

    @PostMapping("/reset")
    public ResponseEntity<String> resetAgentData() {
        log.warn("Agent Reset API was called!");
        agentResetService.resetLearningData();
        return ResponseEntity.ok("Agent memory and logs have been successfully reset.");
    }
}
