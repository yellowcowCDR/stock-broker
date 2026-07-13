package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.portfolio.AccountBalance;

public interface LoadAccountBalancePort {
    AccountBalance loadBalance();
}
