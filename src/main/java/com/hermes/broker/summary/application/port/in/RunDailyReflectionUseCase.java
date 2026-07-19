package com.hermes.broker.summary.application.port.in;

import com.hermes.broker.summary.domain.TradingReflection;
import com.hermes.broker.trading.domain.MarketType;

import java.time.LocalDate;
import java.util.List;

public interface RunDailyReflectionUseCase {
    List<TradingReflection> runDailyReflection(MarketType marketType, LocalDate tradingDate);
}
