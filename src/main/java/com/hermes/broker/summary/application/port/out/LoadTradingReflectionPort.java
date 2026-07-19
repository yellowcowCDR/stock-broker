package com.hermes.broker.summary.application.port.out;

import com.hermes.broker.summary.domain.TradingReflection;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.hermes.broker.trading.domain.MarketType;

public interface LoadTradingReflectionPort {
    List<TradingReflection> loadByTradingDate(LocalDate tradingDate);
    Optional<TradingReflection> loadByIdentity(LocalDate tradingDate, MarketType marketType,
                                               String strategyVersion);
    List<TradingReflection> loadCompleteByStrategyVersion(String strategyVersion);
}
