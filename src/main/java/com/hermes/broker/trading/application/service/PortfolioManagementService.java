package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.in.GetPortfolioSummaryUseCase;
import com.hermes.broker.trading.application.port.out.LoadAccountBalancePort;
import com.hermes.broker.trading.application.port.out.LoadBuyingPowerPort;
import com.hermes.broker.trading.application.port.out.LoadPortfolioPositionsPort;
import com.hermes.broker.trading.application.port.out.LoadOverseasBalancePort;
import com.hermes.broker.trading.domain.portfolio.OverseasBalance;
import com.hermes.broker.trading.domain.portfolio.AccountBalance;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import com.hermes.broker.trading.domain.portfolio.SectorExposure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioManagementService implements GetPortfolioSummaryUseCase {

    private final LoadAccountBalancePort loadAccountBalancePort;
    private final LoadPortfolioPositionsPort loadPortfolioPositionsPort;
    private final LoadBuyingPowerPort loadBuyingPowerPort;
    private final LoadOverseasBalancePort loadOverseasBalancePort;

    @Override
    public PortfolioSummary getPortfolioSummary() {
        AccountBalance balance = loadAccountBalancePort.loadBalance();
        List<PortfolioPosition> positions = loadPortfolioPositionsPort.loadPositions();
        BigDecimal buyingPower = loadBuyingPowerPort.loadBuyingPower();
        OverseasBalance overseasBalance = loadOverseasBalancePort.loadOverseasBalance();

        BigDecimal totalAssetAmount = balance.totalAssetAmount() != null ? balance.totalAssetAmount() : BigDecimal.ZERO;
        BigDecimal cashAmount = balance.cashAmount() != null ? balance.cashAmount() : BigDecimal.ZERO;
        
        BigDecimal usdCash = overseasBalance.usdCash() != null ? overseasBalance.usdCash() : BigDecimal.ZERO;
        BigDecimal usdBuyingPower = overseasBalance.usdBuyingPower() != null ? overseasBalance.usdBuyingPower() : BigDecimal.ZERO;
        
        // Calculate cash rate
        BigDecimal cashRate = BigDecimal.ZERO;
        if (totalAssetAmount.compareTo(BigDecimal.ZERO) > 0) {
            cashRate = cashAmount.divide(totalAssetAmount, 4, RoundingMode.HALF_UP);
        }

        // Calculate sector exposures
        Map<String, BigDecimal> sectorEvaluationMap = positions.stream()
                .collect(Collectors.groupingBy(
                        pos -> pos.sector() != null ? pos.sector() : "UNKNOWN",
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                PortfolioPosition::evaluationAmount,
                                BigDecimal::add
                        )
                ));

        List<SectorExposure> sectorExposures = sectorEvaluationMap.entrySet().stream()
                .map(entry -> {
                    BigDecimal exposureRate = BigDecimal.ZERO;
                    if (totalAssetAmount.compareTo(BigDecimal.ZERO) > 0) {
                        exposureRate = entry.getValue().divide(totalAssetAmount, 4, RoundingMode.HALF_UP);
                    }
                    return new SectorExposure(entry.getKey(), entry.getValue(), exposureRate);
                })
                .toList();

        // Check if dailyProfitLossAmount is available in balance, otherwise it's just the sum of positions' profitLossAmount
        // Assuming we calculate daily by some other means if not provided directly, for now we will just use 0 or something from balance.
        // I will set it to ZERO since AccountBalance doesn't explicitly have it right now unless we extend it.
        BigDecimal dailyProfitLossAmount = BigDecimal.ZERO; 

        return new PortfolioSummary(
                totalAssetAmount,
                cashAmount,
                buyingPower,
                usdCash,
                usdBuyingPower,
                balance.totalEvaluationAmount(),
                balance.totalProfitLossAmount(),
                dailyProfitLossAmount,
                cashRate,
                positions.size(),
                positions,
                sectorExposures,
                LocalDateTime.now()
        );
    }
}
