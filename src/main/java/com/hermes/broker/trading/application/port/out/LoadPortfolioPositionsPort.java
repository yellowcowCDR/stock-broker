package com.hermes.broker.trading.application.port.out;

import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;
import java.util.List;

public interface LoadPortfolioPositionsPort {
    List<PortfolioPosition> loadPositions();
}
