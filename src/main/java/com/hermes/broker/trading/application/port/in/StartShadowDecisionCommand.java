package com.hermes.broker.trading.application.port.in;

public record StartShadowDecisionCommand(
        CreateTradingDecisionCommand decision,
        String exchangeCode
) {
}
