package com.hermes.broker.trading.adapter.in.web;

import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.KisProperties;
import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.property.TradingProperties;
import com.hermes.broker.common.property.OverseasRiskPolicyProperties;
import com.hermes.broker.common.property.KisEnvironment;
import com.hermes.broker.trading.dto.RiskPolicyResponseDto;
import com.hermes.broker.trading.dto.TradingEnvironmentResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/broker/trading")
@RequiredArgsConstructor
public class TradingPolicyController {

    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;
    private final RiskPolicyProperties riskPolicyProperties;
    private final OverseasRiskPolicyProperties overseasRiskPolicyProperties;

    @GetMapping("/environment")
    public ResponseEntity<TradingEnvironmentResponseDto> getEnvironment() {
        return ResponseEntity.ok(new TradingEnvironmentResponseDto(
                kisProperties.environment(),
                tradingProperties.mode(),
                tradingProperties.autonomyMode() == null ? AutonomyMode.ANALYSIS_ONLY : tradingProperties.autonomyMode(),
                tradingProperties.realOrder() != null && tradingProperties.realOrder().enabled(),
                tradingProperties.killSwitch() == null || tradingProperties.killSwitch().enabled(),
                tradingProperties.overseasOrder() != null && tradingProperties.overseasOrder().enabled(),
                kisProperties.environment() == KisEnvironment.MOCK
                        && tradingProperties.overseasOrder() != null
                        && tradingProperties.overseasOrder().enabled(),
                false
        ));
    }

    @GetMapping("/risk-policy")
    public ResponseEntity<RiskPolicyResponseDto> getRiskPolicy() {
        return ResponseEntity.ok(new RiskPolicyResponseDto(
                riskPolicyProperties.version(),
                riskPolicyProperties.dailyMaxLossRate(),
                riskPolicyProperties.maxOrderAmount(),
                riskPolicyProperties.maxDailyTrades(),
                riskPolicyProperties.maxPositionCount(),
                riskPolicyProperties.maxSectorExposureRate(),
                riskPolicyProperties.maxStockExposureRate(),
                riskPolicyProperties.maxPriceDeviationRate(),
                riskPolicyProperties.allowAveragingDown(),
                riskPolicyProperties.allowMarginTrading(),
                riskPolicyProperties.liveTradingEnabled(),
                riskPolicyProperties.requireSectorData(),
                riskPolicyProperties.requireDailyLossData(),
                overseasRiskPolicyProperties.maxOrderAmountUsd(),
                overseasRiskPolicyProperties.maxDailyTrades(),
                overseasRiskPolicyProperties.maxPositionCount(),
                overseasRiskPolicyProperties.maxStockExposureRate(),
                overseasRiskPolicyProperties.allowAveragingDown(),
                false,
                false
        ));
    }
}
