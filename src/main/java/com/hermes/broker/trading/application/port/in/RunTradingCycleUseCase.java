package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.decision.TradingCycleResult;

public interface RunTradingCycleUseCase {
    TradingCycleResult runForStock(String stockCode);
}
