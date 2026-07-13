package com.hermes.broker.agent.application.port.in;

public interface RollbackAgentSkillUseCase {
    void rollback(String currentVersion, String previousVersion, String reason);
}
