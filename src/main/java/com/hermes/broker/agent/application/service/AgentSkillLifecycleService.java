package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.ManageAgentSkillLifecycleUseCase;
import com.hermes.broker.agent.application.port.in.RollbackAgentSkillUseCase;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPerformancePort;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkill;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.property.StrategyEvaluationProperties;
import com.hermes.broker.common.property.TradingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentSkillLifecycleService implements ManageAgentSkillLifecycleUseCase, RollbackAgentSkillUseCase {

    private final LoadAgentSkillPort loadAgentSkillPort;
    private final SaveAgentSkillPort saveAgentSkillPort;
    private final LoadAgentSkillPerformancePort loadAgentSkillPerformancePort;
    private final StrategyEvaluationProperties properties;
    private final TradingProperties tradingProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AgentSkill getByVersion(int version) {
        return requireVersion(version);
    }

    @Override
    @Transactional
    public AgentSkill startShadow(int version, String changedBy, String reason) {
        requireAudit(changedBy, reason);
        AgentSkill target = requireVersion(version);
        return saveAgentSkillPort.save(target.startShadow(changedBy, reason, clock.instant()));
    }

    @Override
    @Transactional
    public AgentSkill evaluateShadow(int version, String evaluatedBy, String reason) {
        requireAudit(evaluatedBy, reason);
        AgentSkill target = requireVersion(version);
        if (target.status() != AgentSkillStatus.SHADOW) {
            throw new IllegalStateException(
                    "Shadow evaluation requires SHADOW status; version " + version
                            + " is " + target.status() + "."
            );
        }

        AgentSkillPerformance performance = loadAgentSkillPerformancePort
                .loadPerformance(Integer.toString(version))
                .orElseThrow(() -> new DataPipelineUnavailableException(
                        "No Broker-generated real shadow performance exists for strategy version "
                                + version + ". Promotion remains blocked."
                ));

        boolean enoughTrades = performance.tradeCount() >= properties.minimumTradeSample();
        boolean enoughDays = performance.evaluationDays() >= properties.minimumEvaluationDays();
        boolean eligible = enoughTrades && enoughDays;

        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("performanceEvaluatedAt", performance.evaluatedAt().toString());
        evaluation.put("tradeCount", performance.tradeCount());
        evaluation.put("requiredTradeCount", properties.minimumTradeSample());
        evaluation.put("evaluationDays", performance.evaluationDays());
        evaluation.put("requiredEvaluationDays", properties.minimumEvaluationDays());
        putIfNotNull(evaluation, "winRate", performance.winRate());
        putIfNotNull(evaluation, "totalReturnRate", performance.totalReturnRate());
        putIfNotNull(evaluation, "profitFactor", performance.profitFactor());
        putIfNotNull(evaluation, "maxDrawdown", performance.maxDrawdown());
        evaluation.put("eligibleForPromotion", eligible);

        String resultReason = reason + (eligible
                ? " | minimum real-data shadow sample satisfied"
                : " | minimum real-data shadow sample NOT satisfied");
        return saveAgentSkillPort.save(target.recordShadowEvaluation(
                evaluation, evaluatedBy, resultReason, clock.instant()));
    }

    @Override
    @Transactional
    public AgentSkill promote(int version, String approvedBy, String reason) {
        requirePaperStrategyChange();
        requireAudit(approvedBy, reason);
        AgentSkill target = requireVersion(version);
        if (target.status() != AgentSkillStatus.SHADOW) {
            throw new IllegalStateException(
                    "Only a SHADOW strategy can be promoted; version " + version
                            + " is " + target.status() + "."
            );
        }
        if (!Boolean.TRUE.equals(target.shadowEvaluation().get("eligibleForPromotion"))) {
            throw new IllegalStateException(
                    "Strategy version " + version
                            + " has no eligible Broker-generated shadow evaluation."
            );
        }

        AgentSkill current = loadAgentSkillPort.loadActiveSkill()
                .orElseThrow(() -> new IllegalStateException("No active strategy exists."));
        if (current.version() == target.version()) {
            throw new IllegalStateException("Strategy version " + version + " is already active.");
        }

        saveAgentSkillPort.save(current.transitionTo(
                AgentSkillStatus.ROLLED_BACK,
                approvedBy,
                "Superseded by approved strategy version " + version + ": " + reason,
                clock.instant()
        ));
        return saveAgentSkillPort.save(target.transitionTo(
                AgentSkillStatus.ACTIVE,
                approvedBy,
                "Explicitly approved after shadow evaluation: " + reason,
                clock.instant()
        ));
    }

    @Override
    @Transactional
    public AgentSkill reject(int version, String rejectedBy, String reason) {
        requireAudit(rejectedBy, reason);
        AgentSkill target = requireVersion(version);
        if (target.status() != AgentSkillStatus.CANDIDATE
                && target.status() != AgentSkillStatus.SHADOW) {
            throw new IllegalStateException(
                    "Only CANDIDATE or SHADOW strategies can be rejected; version "
                            + version + " is " + target.status() + "."
            );
        }
        return saveAgentSkillPort.save(target.transitionTo(
                AgentSkillStatus.REJECTED,
                rejectedBy,
                reason,
                clock.instant()
        ));
    }

    @Override
    @Transactional
    public AgentSkill rollback(int targetVersion, String approvedBy, String reason) {
        requirePaperStrategyChange();
        requireAudit(approvedBy, reason);
        AgentSkill target = requireVersion(targetVersion);
        if (target.status() != AgentSkillStatus.ROLLED_BACK) {
            throw new IllegalStateException(
                    "Rollback target must be a previously active ROLLED_BACK version; version "
                            + targetVersion + " is " + target.status() + "."
            );
        }

        AgentSkill current = loadAgentSkillPort.loadActiveSkill()
                .orElseThrow(() -> new IllegalStateException("No active strategy exists."));
        if (target.version() >= current.version()) {
            throw new IllegalStateException(
                    "Rollback target version must be older than active version " + current.version() + "."
            );
        }

        saveAgentSkillPort.save(current.transitionTo(
                AgentSkillStatus.ROLLED_BACK,
                approvedBy,
                "Rolled back to version " + targetVersion + ": " + reason,
                clock.instant()
        ));
        return saveAgentSkillPort.save(target.transitionTo(
                AgentSkillStatus.ACTIVE,
                approvedBy,
                "Explicit rollback approval from version " + current.version() + ": " + reason,
                clock.instant()
        ));
    }

    private AgentSkill requireVersion(int version) {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        return loadAgentSkillPort.loadByVersion(version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Strategy version " + version + " does not exist."));
    }

    private void requireAudit(String actor, String reason) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    private void requirePaperStrategyChange() {
        if (tradingProperties.mode() == null
                || !"PAPER".equalsIgnoreCase(tradingProperties.mode())) {
            throw new IllegalStateException(
                    "Automatic strategy activation and rollback are restricted to PAPER mode."
            );
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
