package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;

import java.math.BigDecimal;

public interface LoadOverseasAccountDataPort {
    OverseasAccountSnapshot loadUnitedStatesAccount();

    OverseasOrderCapacity loadOrderCapacity(
            String stockCode,
            String exchangeCode,
            BigDecimal orderPrice
    );
}
