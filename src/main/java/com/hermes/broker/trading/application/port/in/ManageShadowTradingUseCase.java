package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;
import com.hermes.broker.trading.domain.decision.ShadowSampleStatus;

import java.time.LocalDate;
import java.util.List;

public interface ManageShadowTradingUseCase {
    ShadowDecisionResult start(StartShadowDecisionCommand command);

    List<ShadowPerformanceSample> settle(MarketType marketType, LocalDate tradingDate);

    List<ShadowPerformanceSample> getSamples(String strategyVersion, ShadowSampleStatus status);
}
