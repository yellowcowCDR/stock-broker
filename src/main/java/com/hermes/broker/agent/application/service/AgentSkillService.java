package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvolveAgentSkillCommand;
import com.hermes.broker.agent.application.port.in.EvolveAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.GetActiveAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.InitializeAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.GetAgentSkillVersionsUseCase;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.common.exception.ActiveAgentSkillNotFoundException;
import com.hermes.broker.common.exception.InvalidAgentSkillParametersException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.Set;
import com.hermes.broker.agent.domain.AgentSkillStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillService implements GetActiveAgentSkillUseCase, EvolveAgentSkillUseCase,
        InitializeAgentSkillUseCase, GetAgentSkillVersionsUseCase {

    private final LoadAgentSkillPort loadAgentSkillPort;
    private final SaveAgentSkillPort saveAgentSkillPort;

    @Override
    @Transactional(readOnly = true)
    public AgentSkill getActiveSkill() {
        return loadAgentSkillPort.loadActiveSkill()
                .orElseThrow(ActiveAgentSkillNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentSkill> getVersions(Set<AgentSkillStatus> statuses) {
        return loadAgentSkillPort.loadVersions(statuses == null ? Set.of() : Set.copyOf(statuses));
    }

    @Override
    @Transactional
    public AgentSkill evolve(EvolveAgentSkillCommand command) {
        if (command.skillParameters() == null || command.skillParameters().isEmpty()) {
            throw new InvalidAgentSkillParametersException("skillParameters cannot be null or empty");
        }
        if (command.description() == null || command.description().isBlank()) {
            throw new InvalidAgentSkillParametersException("description cannot be null or blank");
        }

        AgentSkill activeSkill = loadAgentSkillPort.loadActiveSkill()
                .orElseThrow(ActiveAgentSkillNotFoundException::new);
        AgentSkill latestSkill = loadAgentSkillPort.loadLatestSkill()
                .orElse(activeSkill);

        AgentSkill newSkill = AgentSkill.createCandidate(
                latestSkill,
                activeSkill,
                command.description(),
                command.skillParameters(),
                command.createdBy(),
                java.time.Instant.now()
        );
        
        return saveAgentSkillPort.save(newSkill);
    }

    @Override
    @Transactional
    public void initializeDefaultSkillIfAbsent() {
        if (loadAgentSkillPort.loadActiveSkill().isPresent()) {
            log.info("Active agent skill already exists. Skipping initialization.");
            return;
        }

        AgentSkill defaultSkill = AgentSkill.createInitial(
                "Hermes 기본 퀀트 트레이딩 전략",
                Map.of(
                        "rsiOversold", 30,
                        "rsiOverbought", 70,
                        "maShortPeriod", 5,
                        "maMediumPeriod", 20,
                        "maLongPeriod", 60,
                        "stopLossRate", 0.03,
                        "takeProfitRate", 0.07,
                        "maxPositionCount", 5,
                        "positionSizeRate", 0.2
                )
        );

        saveAgentSkillPort.save(defaultSkill);
        log.info("Initialized default agent skill.");
    }
}
