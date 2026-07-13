package com.hermes.broker.trading.adapter.out.agent;

import com.hermes.broker.trading.application.port.out.InvokeAgentDecisionPort;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DummyAgentDecisionAdapter implements InvokeAgentDecisionPort {

    @Override
    public TradingDecision invoke(TradingFeatureSnapshot snapshot) {
        // 임시 로직: 항상 10주, 현재 가격 50000원으로 BUY 판단 (Mock)
        // 실제로는 Agent 모듈로 HTTP/Grpc/Local Bean 호출
        return new TradingDecision(
                UUID.randomUUID().toString(),
                snapshot.featureId(),
                snapshot.stockCode(),
                TradingDecisionType.BUY,
                "v1.0.0-dummy",
                "Dummy agent decided to BUY",
                new BigDecimal("50000"),
                new BigDecimal("10"),
                LocalDateTime.now()
        );
    }
}
