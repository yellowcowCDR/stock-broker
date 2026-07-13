package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;

public interface SaveTradingFeatureUseCase {
    void save(TradingFeatureSnapshot snapshot);
}
