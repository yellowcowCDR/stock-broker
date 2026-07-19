package com.hermes.broker.trading.adapter.out.agent;

import com.hermes.broker.trading.application.port.out.InvokeAgentDecisionPort;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FailClosedAgentDecisionAdapter implements InvokeAgentDecisionPort {

    private final Clock clock;

    @Override
    public TradingDecision invoke(TradingFeatureSnapshot snapshot) {
        return new TradingDecision(
                UUID.randomUUID().toString(),
                snapshot.featureId(),
                snapshot.stockCode(),
                TradingDecisionType.BLOCK,
                "NO_EMBEDDED_AGENT",
                "Broker has no embedded decision engine; submit Hermes decisions through the order API.",
                null,
                null,
                clock.instant()
        );
    }
}
