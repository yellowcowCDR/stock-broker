package com.hermes.broker.summary.application.port.in;

import com.hermes.broker.summary.domain.TradingReflection;

public interface SaveTradingReflectionUseCase {
    void save(TradingReflection reflection);
}
