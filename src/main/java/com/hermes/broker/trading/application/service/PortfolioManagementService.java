package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.market.application.service.StockSectorResolver;
import com.hermes.broker.market.domain.StockSector;
import com.hermes.broker.trading.application.port.in.GetPortfolioSummaryUseCase;
import com.hermes.broker.trading.application.port.out.LoadAccountBalancePort;
import com.hermes.broker.trading.application.port.out.LoadBuyingPowerPort;
import com.hermes.broker.trading.application.port.out.LoadPortfolioPositionsPort;
import com.hermes.broker.trading.domain.portfolio.AccountBalance;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import com.hermes.broker.trading.domain.portfolio.SectorExposure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
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
    private final Clock clock;
    private final StockSectorResolver stockSectorResolver;

    @Override
    public PortfolioSummary getPortfolioSummary() {
        AccountBalance balance = loadAccountBalancePort.loadBalance();
        List<PortfolioPosition> positions = loadPortfolioPositionsPort.loadPositions();
        BigDecimal buyingPower = loadBuyingPowerPort.loadBuyingPower();
        requireRealBalance(balance, positions, buyingPower);

        BigDecimal totalAssetAmount = balance.totalAssetAmount();
        BigDecimal cashAmount = balance.cashAmount();
        SectorEnrichment sectorEnrichment = enrichSectors(positions, totalAssetAmount);
        positions = sectorEnrichment.positions();
        
        // KRW and USD assets are intentionally not aggregated without an audited FX snapshot.
        // Overseas order risk uses LoadOverseasAccountDataPort directly.
        BigDecimal usdCash = null;
        BigDecimal usdBuyingPower = null;
        
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

        return new PortfolioSummary(
                totalAssetAmount,
                cashAmount,
                buyingPower,
                usdCash,
                usdBuyingPower,
                balance.totalEvaluationAmount(),
                balance.totalProfitLossAmount(),
                balance.previousTotalAssetAmount(),
                balance.dailyAssetChangeAmount(),
                balance.dailyAssetChangeRate(),
                balance.dailyAssetChangeDataComplete(),
                balance.dailyAssetChangeDataSource(),
                cashRate,
                positions.size(),
                positions,
                sectorExposures,
                sectorEnrichment.complete(),
                sectorEnrichment.dataSource(),
                clock.instant()
        );
    }

    private SectorEnrichment enrichSectors(
            List<PortfolioPosition> positions, BigDecimal totalAssetAmount) {
        boolean complete = true;
        String dataSource = positions.isEmpty()
                ? "NOT_APPLICABLE_NO_POSITIONS" : "KIS_OPEN_API:SEARCH_STOCK_INFO";
        java.util.ArrayList<PortfolioPosition> enriched = new java.util.ArrayList<>();

        for (PortfolioPosition position : positions) {
            String sector;
            try {
                StockSector metadata = stockSectorResolver.resolve(
                        position.stockCode(), position.marketType());
                sector = metadata.sectorName();
            } catch (MarketDataUnavailableException e) {
                complete = false;
                sector = "UNKNOWN";
                log.warn("Sector data unavailable for {}:{}: {}",
                        position.marketType(), position.stockCode(), e.getMessage());
            }

            BigDecimal weight = BigDecimal.ZERO;
            if (totalAssetAmount.signum() > 0 && position.evaluationAmount() != null) {
                weight = position.evaluationAmount().divide(totalAssetAmount, 4, RoundingMode.HALF_UP);
            }
            enriched.add(new PortfolioPosition(
                    position.stockCode(),
                    position.stockName(),
                    position.marketType(),
                    sector,
                    position.quantity(),
                    position.availableQuantity(),
                    position.averagePurchasePrice(),
                    position.currentPrice(),
                    position.evaluationAmount(),
                    position.profitLossAmount(),
                    position.profitLossRate(),
                    weight
            ));
        }
        return new SectorEnrichment(List.copyOf(enriched), complete, dataSource);
    }

    private void requireRealBalance(
            AccountBalance balance,
            List<PortfolioPosition> positions,
            BigDecimal buyingPower) {
        if (balance == null || balance.totalAssetAmount() == null || balance.cashAmount() == null
                || balance.totalEvaluationAmount() == null || balance.totalProfitLossAmount() == null
                || positions == null || buyingPower == null) {
            throw new DataPipelineUnavailableException(
                    "KIS account balance, positions and domestic buying power must all be complete."
            );
        }
    }

    private record SectorEnrichment(
            List<PortfolioPosition> positions,
            boolean complete,
            String dataSource
    ) {
    }
}
