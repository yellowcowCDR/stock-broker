package com.hermes.broker.trading.application.port.in;

import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;

public interface GetPortfolioSummaryUseCase {
    PortfolioSummary getPortfolioSummary();
}
