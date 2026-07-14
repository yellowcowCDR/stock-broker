package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.portfolio.OverseasBalance;

public interface LoadOverseasBalancePort {
    OverseasBalance loadOverseasBalance();
}
