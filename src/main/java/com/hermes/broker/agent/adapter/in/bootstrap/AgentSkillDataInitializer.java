package com.hermes.broker.agent.adapter.in.bootstrap;

import com.hermes.broker.agent.application.port.in.InitializeAgentSkillUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSkillDataInitializer implements ApplicationRunner {

    private final InitializeAgentSkillUseCase initializeAgentSkillUseCase;

    @Override
    public void run(ApplicationArguments args) {
        initializeAgentSkillUseCase.initializeDefaultSkillIfAbsent();
    }
}
