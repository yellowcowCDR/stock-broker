package com.hermes.broker.trading.domain.decision;

import com.hermes.broker.trading.domain.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A quote-backed, counterfactual sample. It is deliberately separate from an
 * order/execution record so a SHADOW strategy can never affect the account.
 */
public record ShadowPerformanceSample(
        String sampleId,
        String decisionId,
        String featureId,
        String strategyVersion,
        String stockCode,
        MarketType marketType,
        String exchangeCode,
        TradingDecisionType decisionType,
        BigDecimal referencePrice,
        BigDecimal observedPrice,
        BigDecimal rawReturnRate,
        BigDecimal actionReturnRate,
        LocalDate tradingDate,
        ShadowSampleStatus status,
        String dataSource,
        Instant startedAt,
        Instant observedAt
) {
    public boolean complete() {
        return status == ShadowSampleStatus.COMPLETED
                && observedPrice != null
                && rawReturnRate != null
                && actionReturnRate != null
                && observedAt != null;
    }
}
