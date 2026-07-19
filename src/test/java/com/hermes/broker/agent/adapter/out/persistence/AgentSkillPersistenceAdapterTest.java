package com.hermes.broker.agent.adapter.out.persistence;

import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AgentSkillPersistenceAdapterTest {

    @Test
    void filtersLifecycleStatusesAndKeepsNewestFirstOrder() {
        AgentSkillJpaRepository repository = mock(AgentSkillJpaRepository.class);
        AgentSkillPersistenceAdapter adapter = new AgentSkillPersistenceAdapter(
                repository, new AgentSkillPersistenceMapper());
        given(repository.findAllByOrderByVersionDesc()).willReturn(List.of(
                entity(4, AgentSkillStatus.SHADOW),
                entity(3, AgentSkillStatus.REJECTED),
                entity(2, AgentSkillStatus.CANDIDATE),
                entity(1, AgentSkillStatus.ACTIVE)
        ));

        var result = adapter.loadVersions(Set.of(
                AgentSkillStatus.CANDIDATE, AgentSkillStatus.SHADOW));

        assertThat(result).extracting(AgentSkill::version).containsExactly(4, 2);
        assertThat(result).extracting(AgentSkill::status)
                .containsExactly(AgentSkillStatus.SHADOW, AgentSkillStatus.CANDIDATE);
    }

    private AgentSkillJpaEntity entity(int version, AgentSkillStatus status) {
        Instant time = Instant.parse("2026-07-19T08:00:00Z").plusSeconds(version);
        return AgentSkillJpaEntity.builder()
                .createdAt(time)
                .description("strategy-" + version)
                .isActive(status == AgentSkillStatus.ACTIVE)
                .lifecycleStatus(status)
                .skillParameters(Map.of("rsi", 30 - version))
                .version(version)
                .parentVersion(version == 1 ? null : 1)
                .shadowEvaluation(Map.of())
                .statusReason("test")
                .statusChangedBy("test")
                .statusChangedAt(time)
                .build();
    }
}
