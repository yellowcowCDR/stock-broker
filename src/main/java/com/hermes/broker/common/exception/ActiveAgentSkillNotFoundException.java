package com.hermes.broker.common.exception;

public class ActiveAgentSkillNotFoundException extends RuntimeException {
    public ActiveAgentSkillNotFoundException(String message) {
        super(message);
    }
    
    public ActiveAgentSkillNotFoundException() {
        super("Active Agent Skill not found");
    }
}
