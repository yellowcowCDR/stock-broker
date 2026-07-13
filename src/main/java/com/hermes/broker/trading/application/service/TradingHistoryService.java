package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.in.GetTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.in.GetTradingFeatureUseCase;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class TradingHistoryService implements GetTradingFeatureUseCase, GetTradingDecisionUseCase {

    @Override
    public Optional<TradingFeatureSnapshot> getLatestFeature(String stockCode, MarketType marketType) {
        // Mock implementation
        return Optional.empty();
    }

    @Override
    public List<TradingFeatureSnapshot> getFeaturesByDate(String date) {
        // Mock implementation
        return Collections.emptyList();
    }

    @Override
    public List<TradingDecision> getDecisionsByDate(String date) {
        // Mock implementation
        return Collections.emptyList();
    }
}
