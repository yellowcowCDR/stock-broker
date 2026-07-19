package com.hermes.broker.trading.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioSummary(
        BigDecimal totalAssetAmount,
        BigDecimal cashAmount,
        BigDecimal buyingPower,
        BigDecimal usdCash,
        BigDecimal usdBuyingPower,
        BigDecimal totalEvaluationAmount,
        BigDecimal totalProfitLossAmount,
        BigDecimal previousTotalAssetAmount,
        BigDecimal dailyAssetChangeAmount,
        BigDecimal dailyAssetChangeRate,
        boolean dailyAssetChangeDataComplete,
        String dailyAssetChangeDataSource,
        BigDecimal cashRate,
        int positionCount,
        List<PortfolioPosition> positions,
        List<SectorExposure> sectorExposures,
        boolean sectorDataComplete,
        String sectorDataSource,
        Instant calculatedAt
) {
}
