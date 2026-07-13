package com.hermes.broker.summary.application.port.in;

import com.hermes.broker.summary.domain.TradingReflection;
import java.util.List;

public interface GetTradingReflectionUseCase {
    List<TradingReflection> getReflectionsByDate(String date);
}
