package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvolveAgentSkillCommand;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.common.exception.ActiveAgentSkillNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

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
    void evolve_createsNewVersionAndDeactivatesOld() {
        AgentSkill oldSkill = AgentSkill.createInitial("Old desc", Map.of("key", "val"));
        given(loadPort.loadActiveSkill()).willReturn(Optional.of(oldSkill));
        given(savePort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        EvolveAgentSkillCommand command = new EvolveAgentSkillCommand("New desc", Map.of("key", "newVal"));
        AgentSkill evolved = service.evolve(command);

        assertThat(evolved.version()).isEqualTo(oldSkill.version() + 1);
        assertThat(evolved.active()).isTrue();
        assertThat(evolved.skillParameters()).containsEntry("key", "newVal");
        assertThat(evolved.description()).isEqualTo("New desc");

        ArgumentCaptor<AgentSkill> captor = ArgumentCaptor.forClass(AgentSkill.class);
        verify(savePort, times(2)).save(captor.capture());

        AgentSkill deactivated = captor.getAllValues().get(0);
        assertThat(deactivated.active()).isFalse();
    }
}
