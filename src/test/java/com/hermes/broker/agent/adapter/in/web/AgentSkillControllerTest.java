package com.hermes.broker.agent.adapter.in.web;

import com.hermes.broker.agent.application.port.in.EvolveAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.GetActiveAgentSkillUseCase;
import com.hermes.broker.agent.application.port.in.GetAgentSkillVersionsUseCase;
import com.hermes.broker.agent.application.port.in.ManageAgentSkillLifecycleUseCase;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentSkillControllerTest {

    @Test
    void commaSeparatedStatusQueryReturnsMatchingVersions() throws Exception {
        GetAgentSkillVersionsUseCase versionsUseCase = mock(GetAgentSkillVersionsUseCase.class);
        AgentSkillController controller = new AgentSkillController(
                mock(GetActiveAgentSkillUseCase.class),
                mock(EvolveAgentSkillUseCase.class),
                mock(ManageAgentSkillLifecycleUseCase.class),
                versionsUseCase);
        AgentSkill shadow = new AgentSkill(
                4L,
                Instant.parse("2026-07-19T08:00:00Z"),
                Instant.parse("2026-07-19T08:01:00Z"),
                "shadow",
                AgentSkillStatus.SHADOW,
                Map.of("rsi", 28),
                4,
                3,
                Map.of(),
                "shadow started",
                "hermes");
        given(versionsUseCase.getVersions(Set.of(
                AgentSkillStatus.CANDIDATE, AgentSkillStatus.SHADOW)))
                .willReturn(List.of(shadow));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/internal/agent/skills/versions")
                        .param("status", "CANDIDATE,SHADOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(4))
                .andExpect(jsonPath("$[0].status").value("SHADOW"))
                .andExpect(jsonPath("$[0].parentVersion").value(3));

        verify(versionsUseCase).getVersions(Set.of(
                AgentSkillStatus.CANDIDATE, AgentSkillStatus.SHADOW));
    }
}
