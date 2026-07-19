package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.decision.ShadowPerformanceSample;

public interface SaveShadowPerformanceSamplePort {
    ShadowPerformanceSample save(ShadowPerformanceSample sample);
}
