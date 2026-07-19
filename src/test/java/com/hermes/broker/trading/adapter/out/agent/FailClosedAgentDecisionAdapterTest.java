package com.hermes.broker.trading.adapter.out.agent;

import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.decision.TradingDecision;
import com.hermes.broker.trading.domain.decision.TradingDecisionType;
import com.hermes.broker.trading.domain.decision.TradingFeatureSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FailClosedAgentDecisionAdapterTest {

    @Test
    void blocksCycleWhenNoRealHermesDecisionIsSupplied() {
        Instant now = Instant.parse("2026-07-18T03:00:00Z");
        TradingFeatureSnapshot snapshot = new TradingFeatureSnapshot(
                "feature-1", "005930", MarketType.DOMESTIC,
                Map.of(), Map.of(), Map.of(), now
        );

        TradingDecision decision = new FailClosedAgentDecisionAdapter(
                Clock.fixed(now, ZoneOffset.UTC)
        ).invoke(snapshot);

        assertThat(decision.decisionType()).isEqualTo(TradingDecisionType.BLOCK);
        assertThat(decision.strategyVersion()).isEqualTo("NO_EMBEDDED_AGENT");
        assertThat(decision.recommendedPrice()).isNull();
        assertThat(decision.recommendedQuantity()).isNull();
        assertThat(decision.decidedAt()).isEqualTo(now);
    }
}
