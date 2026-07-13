package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;

public interface SaveTradingFeaturePort {
    void save(TradingFeatureSnapshot snapshot);
}
