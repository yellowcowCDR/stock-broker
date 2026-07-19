package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;

import java.math.BigDecimal;

public interface GetOverseasAccountDataUseCase {
    OverseasAccountSnapshot getUnitedStatesAccount();

    OverseasOrderCapacity getOrderCapacity(
            String stockCode,
            String exchangeCode,
            BigDecimal orderPrice
    );
}
