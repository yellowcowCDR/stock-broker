package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvolveAgentSkillCommand;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.common.exception.ActiveAgentSkillNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class AgentSkillServiceTest {

    private LoadAgentSkillPort loadPort;
    private SaveAgentSkillPort savePort;
    private AgentSkillService service;

    @BeforeEach
    void setUp() {
        loadPort = mock(LoadAgentSkillPort.class);
        savePort = mock(SaveAgentSkillPort.class);
        service = new AgentSkillService(loadPort, savePort);
    }

    @Test
    void getActiveSkill_throwsExceptionIfNotFound() {
        given(loadPort.loadActiveSkill()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveSkill())
                .isInstanceOf(ActiveAgentSkillNotFoundException.class);
    }

    @Test
    void evolve_createsCandidateAndKeepsActiveStrategy() {
        AgentSkill oldSkill = AgentSkill.createInitial("Old desc", Map.of("key", "val"));
        given(loadPort.loadActiveSkill()).willReturn(Optional.of(oldSkill));
        given(loadPort.loadLatestSkill()).willReturn(Optional.of(oldSkill));
        given(savePort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        EvolveAgentSkillCommand command = new EvolveAgentSkillCommand("New desc", Map.of("key", "newVal"));
        AgentSkill evolved = service.evolve(command);

        assertThat(evolved.version()).isEqualTo(oldSkill.version() + 1);
        assertThat(evolved.active()).isFalse();
        assertThat(evolved.status()).isEqualTo(AgentSkillStatus.CANDIDATE);
        assertThat(evolved.parentVersion()).isEqualTo(oldSkill.version());
        assertThat(evolved.skillParameters()).containsEntry("key", "newVal");
        assertThat(evolved.description()).isEqualTo("New desc");

        ArgumentCaptor<AgentSkill> captor = ArgumentCaptor.forClass(AgentSkill.class);
        verify(savePort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AgentSkillStatus.CANDIDATE);
        assertThat(oldSkill.active()).isTrue();
    }

    @Test
    void getVersionsDelegatesLifecycleFilterWithoutChangingIt() {
        AgentSkill candidate = AgentSkill.createCandidate(
                AgentSkill.createInitial("active", Map.of("rsi", 30)),
                AgentSkill.createInitial("active", Map.of("rsi", 30)),
                "candidate", Map.of("rsi", 28), "hermes", java.time.Instant.now());
        given(loadPort.loadVersions(Set.of(AgentSkillStatus.CANDIDATE, AgentSkillStatus.SHADOW)))
                .willReturn(List.of(candidate));

        var result = service.getVersions(Set.of(AgentSkillStatus.CANDIDATE, AgentSkillStatus.SHADOW));

        assertThat(result).containsExactly(candidate);
        verify(loadPort).loadVersions(Set.of(AgentSkillStatus.CANDIDATE, AgentSkillStatus.SHADOW));
    }
}
