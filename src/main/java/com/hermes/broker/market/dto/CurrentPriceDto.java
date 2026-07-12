package com.hermes.broker.market.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CurrentPriceDto {
    private String stockCode;
    private BigDecimal currentPrice;
    private BigDecimal changeRate;
    private Long accumulatedVolume;
    private TechnicalIndicators technicalIndicators;
}
