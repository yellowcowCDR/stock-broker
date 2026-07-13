package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.in.SaveTradingDecisionUseCase;
import com.hermes.broker.trading.application.port.out.SaveTradingDecisionPort;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradingDecisionService implements SaveTradingDecisionUseCase {

    private final SaveTradingDecisionPort saveTradingDecisionPort;

    @Override
    public void save(TradingDecision decision) {
        saveTradingDecisionPort.save(decision);
    }
}
