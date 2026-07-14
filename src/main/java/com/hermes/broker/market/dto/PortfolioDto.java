package com.hermes.broker.market.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PortfolioDto {
    private BigDecimal totalAsset;
    private BigDecimal availableCash;
    private BigDecimal usdCash;
    private BigDecimal usdBuyingPower;
    private List<StockHolding> holdings;

    @Getter
    @Builder
    public static class StockHolding {
        private String stockCode;
        private String stockName;
        private int quantity;
        private BigDecimal averageBuyPrice;
        private BigDecimal currentPrice;
        private BigDecimal returnRate;
    }
}
