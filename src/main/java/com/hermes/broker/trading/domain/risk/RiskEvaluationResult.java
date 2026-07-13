package com.hermes.broker.trading.domain.risk;

import java.util.List;
import java.util.Map;

public record RiskEvaluationResult(
        boolean allowed,
        RiskDecision decision,
        List<String> reasons,
        Map<String, Object> snapshot
) {
}
