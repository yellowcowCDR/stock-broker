package com.hermes.broker.agent.application.service;

import com.hermes.broker.agent.application.port.in.EvaluateStrategyPerformanceUseCase;
import com.hermes.broker.agent.application.port.in.GetStrategyPerformanceUseCase;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPerformancePort;
import com.hermes.broker.agent.application.port.out.SaveAgentSkillPerformancePort;
import com.hermes.broker.agent.application.port.out.LoadAgentSkillPort;
import com.hermes.broker.agent.domain.AgentSkillPerformance;
import com.hermes.broker.agent.domain.AgentSkillStatus;
import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.summary.application.port.out.LoadTradingReflectionPort;
import com.hermes.broker.summary.domain.TradeReview;
import com.hermes.broker.summary.domain.TradingReflection;
import com.hermes.broker.trading.application.port.out.LoadShadowPerformanceSamplePort;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StrategyPerformanceService implements EvaluateStrategyPerformanceUseCase, GetStrategyPerformanceUseCase {

    private final LoadAgentSkillPerformancePort loadAgentSkillPerformancePort;
    private final SaveAgentSkillPerformancePort saveAgentSkillPerformancePort;
    private final LoadTradingReflectionPort loadTradingReflectionPort;
    private final LoadShadowPerformanceSamplePort loadShadowPerformanceSamplePort;
    private final LoadAgentSkillPort loadAgentSkillPort;

    @Override
    public AgentSkillPerformance evaluate(String strategyVersion) {
        if (strategyVersion == null || strategyVersion.isBlank()) {
            throw new IllegalArgumentException("strategyVersion is required.");
        }
        List<TradingReflection> reflections = loadTradingReflectionPort
                .loadCompleteByStrategyVersion(strategyVersion);
        if (reflections.isEmpty()) {
            if (isShadowVersion(strategyVersion)) {
                return evaluateShadowSamples(strategyVersion);
            }
            throw new DataPipelineUnavailableException(
                    "No complete Broker reflections exist for strategy version " + strategyVersion
                            + "; synthetic metrics are disabled.");
        }
        List<BigDecimal> returns = reflections.stream()
                .map(TradingReflection::dailyReturnRate)
                .filter(Objects::nonNull)
                .toList();
        if (returns.size() != reflections.size()) {
            throw new DataPipelineUnavailableException(
                    "A complete reflection is missing its portfolio return; performance was not saved.");
        }

        List<BigDecimal> profits = returns.stream().filter(value -> value.signum() > 0).toList();
        List<BigDecimal> losses = returns.stream().filter(value -> value.signum() < 0).toList();
        BigDecimal averageProfit = average(profits);
        BigDecimal averageLoss = average(losses);
        BigDecimal profitLossRatio = averageProfit == null || averageLoss == null
                ? null : divide(averageProfit, averageLoss.abs());
        BigDecimal grossProfit = profits.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = losses.stream().map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? null : divide(grossProfit, grossLoss);

        int tradeCount = reflections.stream()
                .flatMap(reflection -> reflection.reviews().stream())
                .map(TradeReview::executedQuantity)
                .filter(Objects::nonNull)
                .mapToInt(quantity -> quantity.signum() > 0 ? 1 : 0)
                .sum();
        AgentSkillPerformance performance = new AgentSkillPerformance(
                strategyVersion,
                tradeCount,
                reflections.size(),
                null, // Per-trade realized P/L is not available; winning-day rate is not substituted.
                compoundedReturn(returns),
                average(returns),
                averageProfit,
                averageLoss,
                profitLossRatio,
                profitFactor,
                sharpeRatio(returns),
                maxDrawdown(returns),
                null, // Requires post-decision counterfactual prices.
                null, // Requires post-BLOCK counterfactual prices.
                Instant.now()
        );
        saveAgentSkillPerformancePort.save(performance);
        return performance;
    }

    private AgentSkillPerformance evaluateShadowSamples(String strategyVersion) {
        List<ShadowPerformanceSample> samples = loadShadowPerformanceSamplePort
                .loadByStrategyVersion(strategyVersion, ShadowSampleStatus.COMPLETED).stream()
                .filter(ShadowPerformanceSample::complete)
                .toList();
        if (samples.isEmpty()) {
            throw new DataPipelineUnavailableException(
                    "No complete Broker reflections or real-quote shadow samples exist for strategy version "
                            + strategyVersion + "; synthetic metrics are disabled.");
        }

        Map<java.time.LocalDate, List<BigDecimal>> returnsByDay = new LinkedHashMap<>();
        for (ShadowPerformanceSample sample : samples) {
            returnsByDay.computeIfAbsent(sample.tradingDate(), ignored -> new java.util.ArrayList<>())
                    .add(sample.actionReturnRate());
        }
        List<BigDecimal> dailyReturns = returnsByDay.values().stream()
                .map(this::average)
                .toList();
        List<ShadowPerformanceSample> tradeSamples = samples.stream()
                .filter(sample -> sample.decisionType() == TradingDecisionType.BUY
                        || sample.decisionType() == TradingDecisionType.SELL)
                .toList();
        List<BigDecimal> tradeReturns = tradeSamples.stream()
                .map(ShadowPerformanceSample::actionReturnRate)
                .toList();
        List<BigDecimal> profits = tradeReturns.stream()
                .filter(value -> value.signum() > 0).toList();
        List<BigDecimal> losses = tradeReturns.stream()
                .filter(value -> value.signum() < 0).toList();
        BigDecimal averageProfit = average(profits);
        BigDecimal averageLoss = average(losses);
        BigDecimal grossProfit = profits.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = losses.stream().map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AgentSkillPerformance performance = new AgentSkillPerformance(
                strategyVersion,
                tradeSamples.size(),
                returnsByDay.size(),
                tradeSamples.isEmpty() ? null : BigDecimal.valueOf(profits.size())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(tradeSamples.size()), 6, RoundingMode.HALF_UP),
                compoundedReturn(dailyReturns),
                average(dailyReturns),
                averageProfit,
                averageLoss,
                averageProfit == null || averageLoss == null
                        ? null : divide(averageProfit, averageLoss.abs()),
                grossLoss.signum() == 0 ? null : divide(grossProfit, grossLoss),
                sharpeRatio(dailyReturns),
                maxDrawdown(dailyReturns),
                calculateHoldAccuracy(samples),
                calculateRiskBlockEffect(samples),
                Instant.now()
        );
        saveAgentSkillPerformancePort.save(performance);
        return performance;
    }

    private boolean isShadowVersion(String strategyVersion) {
        try {
            int version = Integer.parseInt(strategyVersion);
            return loadAgentSkillPort.loadByVersion(version)
                    .map(skill -> skill.status() == AgentSkillStatus.SHADOW)
                    .orElse(false);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private BigDecimal calculateHoldAccuracy(List<ShadowPerformanceSample> samples) {
        List<ShadowPerformanceSample> holds = samples.stream()
                .filter(sample -> sample.decisionType() == TradingDecisionType.HOLD)
                .toList();
        if (holds.isEmpty()) {
            return null;
        }
        long neutral = holds.stream()
                .filter(sample -> sample.rawReturnRate().abs()
                        .compareTo(BigDecimal.ONE) <= 0)
                .count();
        return BigDecimal.valueOf(neutral).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(holds.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRiskBlockEffect(List<ShadowPerformanceSample> samples) {
        List<BigDecimal> avoidedReturns = samples.stream()
                .filter(sample -> sample.decisionType() == TradingDecisionType.BLOCK)
                .map(sample -> sample.rawReturnRate().negate())
                .toList();
        return average(avoidedReturns);
    }

    @Override
    public AgentSkillPerformance getPerformance(String version) {
        return loadAgentSkillPerformancePort.loadPerformance(version)
                .orElseThrow(() -> new DataPipelineUnavailableException(
                        "No persisted real performance exists for strategy version " + version + "."
                ));
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal compoundedReturn(List<BigDecimal> returns) {
        BigDecimal capital = BigDecimal.ONE;
        for (BigDecimal dailyReturn : returns) {
            capital = capital.multiply(BigDecimal.ONE.add(
                    dailyReturn.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)));
        }
        return capital.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal sharpeRatio(List<BigDecimal> returns) {
        if (returns.size() < 2) {
            return null;
        }
        double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
                .sum() / (returns.size() - 1);
        double standardDeviation = Math.sqrt(variance);
        if (standardDeviation == 0.0d) {
            return null;
        }
        return BigDecimal.valueOf(mean / standardDeviation * Math.sqrt(252.0d))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal maxDrawdown(List<BigDecimal> returns) {
        BigDecimal capital = BigDecimal.ONE;
        BigDecimal peak = BigDecimal.ONE;
        BigDecimal worst = BigDecimal.ZERO;
        for (BigDecimal dailyReturn : returns) {
            capital = capital.multiply(BigDecimal.ONE.add(
                    dailyReturn.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)));
            if (capital.compareTo(peak) > 0) {
                peak = capital;
            }
            BigDecimal drawdown = capital.subtract(peak)
                    .divide(peak, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (drawdown.compareTo(worst) < 0) {
                worst = drawdown;
            }
        }
        return worst.setScale(6, RoundingMode.HALF_UP);
    }
}
