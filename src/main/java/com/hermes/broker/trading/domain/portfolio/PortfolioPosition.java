package com.hermes.broker.trading.domain.portfolio;

import com.hermes.broker.trading.domain.MarketType;

import java.math.BigDecimal;

public record PortfolioPosition(
        String stockCode,
        String stockName,
        MarketType marketType,
        String sector,
        BigDecimal quantity,
        BigDecimal availableQuantity,
        BigDecimal averagePurchasePrice,
        BigDecimal currentPrice,
        BigDecimal evaluationAmount,
        BigDecimal profitLossAmount,
        BigDecimal profitLossRate,
        BigDecimal portfolioWeight
) {
}
