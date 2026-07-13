package com.hermes.broker.summary.application.port.out;

import com.hermes.broker.summary.domain.TradingReflection;

public interface SaveTradingReflectionPort {
    void save(TradingReflection reflection);
}
