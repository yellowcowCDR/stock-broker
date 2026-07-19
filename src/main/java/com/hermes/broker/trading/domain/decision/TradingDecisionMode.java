package com.hermes.broker.trading.domain.decision;

/**
 * ACTIVE decisions may enter the Broker order pipeline. SHADOW decisions are
 * counterfactual observations only and can never be submitted as orders.
 */
public enum TradingDecisionMode {
    ACTIVE,
    SHADOW
}
