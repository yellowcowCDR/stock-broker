package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PortfolioSummary(
        BigDecimal totalAssetAmount,
        BigDecimal cashAmount,
        BigDecimal buyingPower,
        BigDecimal totalEvaluationAmount,
        BigDecimal totalProfitLossAmount,
        BigDecimal dailyProfitLossAmount,
        BigDecimal cashRate,
        int positionCount,
        List<PortfolioPosition> positions,
        List<SectorExposure> sectorExposures,
        LocalDateTime calculatedAt
) {
}
