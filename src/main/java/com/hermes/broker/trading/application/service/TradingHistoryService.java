package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.in.GetTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.GetTradingFeatureUseCase;
import com.hermes.broker.trading.application.port.out.LoadTradingDecisionPort;
import com.hermes.broker.trading.application.port.out.LoadTradingFeaturePort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradingHistoryService implements GetTradingFeatureUseCase, GetTradingDecisionUseCase {

    private final LoadTradingFeaturePort loadTradingFeaturePort;
    private final LoadTradingDecisionPort loadTradingDecisionPort;

    @Override
    public Optional<TradingFeatureSnapshot> getLatestFeature(String stockCode, MarketType marketType) {
        return loadTradingFeaturePort.loadLatest(stockCode, marketType);
    }

    @Override
    public List<TradingFeatureSnapshot> getFeaturesByDate(String date) {
        TimeRange range = utcDay(date);
        return loadTradingFeaturePort.loadBetween(range.start(), range.end());
    }

    @Override
    public List<TradingDecision> getDecisionsByDate(String date) {
        TimeRange range = utcDay(date);
        return loadTradingDecisionPort.loadBetween(range.start(), range.end());
    }

    private TimeRange utcDay(String value) {
        LocalDate date = LocalDate.parse(value);
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TimeRange(start, start.plusSeconds(86_400));
    }

    private record TimeRange(Instant start, Instant end) {
    }
}
