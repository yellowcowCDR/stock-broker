package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.in.SaveTradingFeatureUseCase;
import com.hermes.broker.trading.application.port.out.SaveTradingFeaturePort;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradingFeatureService implements SaveTradingFeatureUseCase {

    private final SaveTradingFeaturePort saveTradingFeaturePort;

    @Override
    public void save(TradingFeatureSnapshot snapshot) {
        saveTradingFeaturePort.save(snapshot);
    }
}
