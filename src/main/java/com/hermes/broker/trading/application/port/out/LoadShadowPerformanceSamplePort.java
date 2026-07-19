package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadShadowPerformanceSamplePort {
    Optional<ShadowPerformanceSample> loadByDecisionId(String decisionId);

    List<ShadowPerformanceSample> loadByStrategyVersion(
            String strategyVersion, ShadowSampleStatus status);

    List<ShadowPerformanceSample> loadByMarketAndTradingDateAndStatus(
            MarketType marketType, LocalDate tradingDate, ShadowSampleStatus status);
}
