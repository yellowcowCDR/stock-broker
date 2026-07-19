package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.common.property.OverseasRiskPolicyProperties;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.trading.application.port.in.EvaluateOrderRiskUseCase;
import com.hermes.broker.trading.application.port.in.GetPortfolioSummaryUseCase;
import com.hermes.broker.trading.application.port.in.OrderRiskCommand;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.application.port.out.LoadOverseasAccountDataPort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import com.hermes.broker.trading.domain.portfolio.SectorExposure;
import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;
import com.hermes.broker.trading.domain.portfolio.OverseasPosition;
import com.hermes.broker.trading.domain.risk.RiskDecision;
import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEvaluationService implements EvaluateOrderRiskUseCase {

    private final RiskPolicyProperties properties;
    private final GetPortfolioSummaryUseCase getPortfolioSummaryUseCase;
    private final TradingLogRepository tradingLogRepository;
    private final TradingTimeService tradingTimeService;
    private final LoadOverseasAccountDataPort loadOverseasAccountDataPort;
    private final OverseasRiskPolicyProperties overseasProperties;
    private final com.hermes.broker.market.application.service.StockSectorResolver stockSectorResolver;

    @Override
    public RiskEvaluationResult evaluate(OrderRiskCommand command) {
        List<String> reasons = new ArrayList<>();
        Map<String, Object> snapshot = new HashMap<>();

        if (command.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("Invalid quantity: " + command.quantity());
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INVALID_QUANTITY, reasons, snapshot);
        }

        if (command.price() == null || command.price().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("Invalid price: " + command.price());
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INVALID_PRICE, reasons, snapshot);
        }

        if (command.marketType() == MarketType.OVERSEAS) {
            return evaluateOverseas(command, reasons, snapshot);
        }

        PortfolioSummary portfolio = getPortfolioSummaryUseCase.getPortfolioSummary();
        snapshot.put("totalAssetAmount", portfolio.totalAssetAmount());
        snapshot.put("previousTotalAssetAmount", portfolio.previousTotalAssetAmount());
        snapshot.put("dailyAssetChangeAmount", portfolio.dailyAssetChangeAmount());
        snapshot.put("dailyAssetChangeRate", portfolio.dailyAssetChangeRate());
        snapshot.put("dailyAssetChangeDataComplete", portfolio.dailyAssetChangeDataComplete());
        snapshot.put("dailyAssetChangeDataSource", portfolio.dailyAssetChangeDataSource());
        snapshot.put("sectorDataComplete", portfolio.sectorDataComplete());
        snapshot.put("sectorDataSource", portfolio.sectorDataSource());
        snapshot.put("positionCount", portfolio.positionCount());
        snapshot.put("buyingPower", portfolio.buyingPower());
        snapshot.put("usdBuyingPower", portfolio.usdBuyingPower());
        snapshot.put("marketContextId", command.marketContextId());
        snapshot.put("marketRiskMultiplier", command.marketRiskMultiplier());

        if (command.orderType() == OrderType.SELL) {
            Optional<PortfolioPosition> position = portfolio.positions().stream()
                    .filter(p -> p.marketType() == command.marketType())
                    .filter(p -> p.stockCode().equals(command.stockCode()))
                    .findFirst();
            if (position.isEmpty() || position.get().availableQuantity() == null
                    || position.get().availableQuantity().compareTo(command.quantity()) < 0) {
                reasons.add("Insufficient sellable quantity for " + command.stockCode());
                return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INSUFFICIENT_POSITION, reasons, snapshot);
            }
            return new RiskEvaluationResult(true, RiskDecision.ALLOWED, reasons, snapshot);
        }

        if (properties.requireDailyLossData() && (!portfolio.dailyAssetChangeDataComplete()
                || portfolio.dailyAssetChangeRate() == null)) {
            reasons.add("Daily loss data is incomplete.");
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }

        if (properties.requireSectorData() && (!portfolio.sectorDataComplete()
                || command.sector() == null || command.sector().isBlank()
                || "UNKNOWN".equalsIgnoreCase(command.sector()))) {
            reasons.add("Sector data is required for entry risk evaluation.");
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }
        
        BigDecimal orderAmount = command.price().multiply(command.quantity());
        snapshot.put("orderAmount", orderAmount);

        // 1. 최대 주문 금액
        BigDecimal marketRiskMultiplier = command.marketRiskMultiplier();
        if (marketRiskMultiplier == null || marketRiskMultiplier.compareTo(BigDecimal.ZERO) <= 0
                || marketRiskMultiplier.compareTo(BigDecimal.ONE) > 0) {
            reasons.add("Market risk multiplier is missing or invalid.");
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }
        BigDecimal effectiveMaxOrderAmount = properties.maxOrderAmount().multiply(marketRiskMultiplier);
        snapshot.put("effectiveMaxOrderAmount", effectiveMaxOrderAmount);
        if (orderAmount.compareTo(effectiveMaxOrderAmount) > 0) {
            reasons.add("Order amount exceeds max limit: " + orderAmount);
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_MAX_ORDER_AMOUNT, reasons, snapshot);
        }

        // 2. 주문 가능 잔고 확인
        BigDecimal availableBuyingPower = command.marketType() == MarketType.OVERSEAS
                ? portfolio.usdBuyingPower()
                : portfolio.buyingPower();
        snapshot.put("availableBuyingPower", availableBuyingPower);
        if (availableBuyingPower == null) {
            reasons.add("Buying-power data is incomplete for " + command.marketType() + ".");
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }
        if (orderAmount.compareTo(availableBuyingPower) > 0 && !properties.allowMarginTrading()) {
            reasons.add("Insufficient buying power. Available: " + availableBuyingPower);
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INSUFFICIENT_BALANCE, reasons, snapshot);
        }

        // 3. 일일 최대 손실률
        BigDecimal assetChangeRate = portfolio.dailyAssetChangeRate();
        BigDecimal dailyLossRate = assetChangeRate != null && assetChangeRate.signum() < 0
                ? assetChangeRate.abs() : BigDecimal.ZERO;
        snapshot.put("dailyLossRate", dailyLossRate);
        if (dailyLossRate.compareTo(properties.dailyMaxLossRate()) > 0) {
            reasons.add("Daily max loss rate exceeded: " + dailyLossRate);
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_DAILY_LOSS, reasons, snapshot);
        }

        // 4. 보유 종목 수 초과
        // 현재 주문하려는 종목이 이미 보유 중인지 확인
        boolean alreadyHolding = portfolio.positions().stream()
                .anyMatch(p -> p.stockCode().equals(command.stockCode()));

        if (!alreadyHolding && portfolio.positionCount() >= properties.maxPositionCount()) {
            reasons.add("Max position count exceeded: " + portfolio.positionCount());
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_MAX_POSITION_COUNT, reasons, snapshot);
        }

        // 5. 물타기(Averaging Down) 제한 확인
        if (alreadyHolding && !properties.allowAveragingDown()) {
            Optional<PortfolioPosition> pos = portfolio.positions().stream()
                    .filter(p -> p.stockCode().equals(command.stockCode())).findFirst();
            if (pos.isPresent() && pos.get().profitLossRate().compareTo(BigDecimal.ZERO) < 0) {
                reasons.add("Averaging down is not allowed.");
                return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_AVERAGING_DOWN, reasons, snapshot);
            }
        }

        // 6. 단일 종목 집중도 한도 (Max Stock Exposure)
        BigDecimal currentStockExposure = portfolio.positions().stream()
                .filter(p -> p.stockCode().equals(command.stockCode()))
                .map(PortfolioPosition::evaluationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 매수는 현금이 주식으로 바뀌는 것이므로 총자산 자체를 증가시키지 않는다.
        BigDecimal projectedTotalAsset = portfolio.totalAssetAmount();
        BigDecimal projectedStockExposure = currentStockExposure.add(orderAmount);
        
        BigDecimal stockExposureRate = BigDecimal.ZERO;
        if (projectedTotalAsset.compareTo(BigDecimal.ZERO) > 0) {
            stockExposureRate = projectedStockExposure.divide(projectedTotalAsset, 4, RoundingMode.HALF_UP);
        }
        
        snapshot.put("stockExposureRate", stockExposureRate);
        if (stockExposureRate.compareTo(properties.maxStockExposureRate()) > 0) {
            reasons.add("Max stock exposure rate exceeded: " + stockExposureRate);
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_STOCK_EXPOSURE, reasons, snapshot);
        }

        // 7. 업종 집중도 한도 (Max Sector Exposure)
        if (command.sector() != null && !command.sector().equals("UNKNOWN")) {
            BigDecimal currentSectorExposure = portfolio.sectorExposures().stream()
                    .filter(s -> s.sector().equals(command.sector()))
                    .map(SectorExposure::evaluationAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal projectedSectorExposure = currentSectorExposure.add(orderAmount);
            BigDecimal sectorExposureRate = BigDecimal.ZERO;
            if (projectedTotalAsset.compareTo(BigDecimal.ZERO) > 0) {
                sectorExposureRate = projectedSectorExposure.divide(projectedTotalAsset, 4, RoundingMode.HALF_UP);
            }
            
            snapshot.put("sectorExposureRate", sectorExposureRate);
            if (sectorExposureRate.compareTo(properties.maxSectorExposureRate()) > 0) {
                reasons.add("Max sector exposure rate exceeded: " + sectorExposureRate);
                return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_SECTOR_EXPOSURE, reasons, snapshot);
            }
        }

        // 8. Broker DB 기준 동일 종목/동일 방향 진행 주문 확인
        boolean duplicateOpenOrder = tradingLogRepository.existsOpenOrder(
                command.accountKey(), command.marketType(), command.stockCode(), command.orderType());
        if (duplicateOpenOrder) {
            reasons.add("Open BUY order already exists for " + command.stockCode());
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_DUPLICATE_ORDER, reasons, snapshot);
        }

        var tradingDay = tradingTimeService.currentMarketDay(command.marketType());
        long dailyTradeCount = tradingLogRepository.countSubmittedOrders(
                command.accountKey(), command.marketType(),
                tradingDay.startInclusive(), tradingDay.endExclusive());
        snapshot.put("dailyTradeCount", dailyTradeCount);
        if (dailyTradeCount >= properties.maxDailyTrades()) {
            reasons.add("Max daily trades exceeded: " + dailyTradeCount);
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_MAX_DAILY_TRADES, reasons, snapshot);
        }

        return new RiskEvaluationResult(true, RiskDecision.ALLOWED, reasons, snapshot);
    }

    private RiskEvaluationResult evaluateOverseas(
            OrderRiskCommand command,
            List<String> reasons,
            Map<String, Object> snapshot
    ) {
        OverseasAccountSnapshot account = loadOverseasAccountDataPort.loadUnitedStatesAccount();
        if (account == null || !account.complete() || !"USD".equalsIgnoreCase(account.currency())
                || account.availableForUse() == null || account.cashBalance() == null
                || account.positions() == null) {
            reasons.add("Complete USD account data is unavailable.");
            return blocked(RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }

        snapshot.put("currency", "USD");
        snapshot.put("exchangeCode", command.exchangeCode());
        snapshot.put("usdCashBalance", account.cashBalance());
        snapshot.put("usdAvailableForUse", account.availableForUse());
        snapshot.put("overseasAccountDataSource", account.dataSource());
        snapshot.put("overseasAccountFetchedAt", account.fetchedAt());

        Optional<OverseasPosition> existing = account.positions().stream()
                .filter(position -> position.stockCode().equalsIgnoreCase(command.stockCode()))
                .filter(position -> position.exchangeCode().equalsIgnoreCase(command.exchangeCode()))
                .findFirst();

        if (command.orderType() == OrderType.SELL) {
            if (existing.isEmpty() || existing.get().sellableQuantity() == null
                    || existing.get().sellableQuantity().compareTo(command.quantity()) < 0) {
                reasons.add("Insufficient USD-account sellable quantity for " + command.stockCode());
                return blocked(RiskDecision.BLOCKED_BY_INSUFFICIENT_POSITION, reasons, snapshot);
            }
            snapshot.put("sellableQuantity", existing.get().sellableQuantity());
            return allowed(reasons, snapshot);
        }

        BigDecimal multiplier = command.marketRiskMultiplier();
        if (multiplier == null || multiplier.signum() <= 0 || multiplier.compareTo(BigDecimal.ONE) > 0) {
            reasons.add("Market risk multiplier is missing or invalid.");
            return blocked(RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }

        BigDecimal orderAmount = command.price().multiply(command.quantity());
        BigDecimal maxOrderAmount = requiredPositive(
                overseasProperties.maxOrderAmountUsd(), "overseas maxOrderAmountUsd")
                .multiply(multiplier);
        snapshot.put("orderAmountUsd", orderAmount);
        snapshot.put("effectiveMaxOrderAmountUsd", maxOrderAmount);
        if (orderAmount.compareTo(maxOrderAmount) > 0) {
            reasons.add("USD order amount exceeds max limit: " + orderAmount);
            return blocked(RiskDecision.BLOCKED_BY_MAX_ORDER_AMOUNT, reasons, snapshot);
        }

        OverseasOrderCapacity capacity = loadOverseasAccountDataPort.loadOrderCapacity(
                command.stockCode(), command.exchangeCode(), command.price());
        if (capacity == null || !capacity.complete() || !"USD".equalsIgnoreCase(capacity.currency())
                || !capacity.stockCode().equalsIgnoreCase(command.stockCode())
                || !capacity.exchangeCode().equalsIgnoreCase(command.exchangeCode())
                || capacity.orderableForeignAmount() == null || capacity.orderableQuantity() == null
                || capacity.maximumOrderableQuantity() == null) {
            reasons.add("Complete symbol-specific USD order capacity is unavailable.");
            return blocked(RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }
        snapshot.put("orderCapacityDataSource", capacity.dataSource());
        snapshot.put("orderCapacityFetchedAt", capacity.fetchedAt());
        snapshot.put("orderableForeignAmountUsd", capacity.orderableForeignAmount());
        snapshot.put("orderableQuantity", capacity.orderableQuantity());
        snapshot.put("maximumOrderableQuantity", capacity.maximumOrderableQuantity());
        if (orderAmount.compareTo(account.availableForUse()) > 0
                || orderAmount.compareTo(capacity.orderableForeignAmount()) > 0
                || command.quantity().compareTo(capacity.orderableQuantity()) > 0
                || command.quantity().compareTo(capacity.maximumOrderableQuantity()) > 0) {
            reasons.add("Insufficient USD buying power or KIS orderable quantity.");
            return blocked(RiskDecision.BLOCKED_BY_INSUFFICIENT_BALANCE, reasons, snapshot);
        }

        if (existing.isEmpty() && account.positions().size() >= overseasProperties.maxPositionCount()) {
            reasons.add("Overseas max position count exceeded: " + account.positions().size());
            return blocked(RiskDecision.BLOCKED_BY_MAX_POSITION_COUNT, reasons, snapshot);
        }
        if (existing.isPresent() && !overseasProperties.allowAveragingDown()
                && existing.get().profitLossRate() != null
                && existing.get().profitLossRate().signum() < 0) {
            reasons.add("Overseas averaging down is not allowed.");
            return blocked(RiskDecision.BLOCKED_BY_AVERAGING_DOWN, reasons, snapshot);
        }

        BigDecimal totalAssetsUsd = account.cashBalance().add(account.positions().stream()
                .map(OverseasPosition::evaluationAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (totalAssetsUsd.signum() <= 0) {
            reasons.add("USD total asset amount is unavailable or non-positive.");
            return blocked(RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
        }
        BigDecimal currentStockExposure = existing.map(OverseasPosition::evaluationAmount)
                .orElse(BigDecimal.ZERO);
        BigDecimal stockExposureRate = currentStockExposure.add(orderAmount)
                .divide(totalAssetsUsd, 4, RoundingMode.HALF_UP);
        snapshot.put("totalAssetsUsd", totalAssetsUsd);
        snapshot.put("stockExposureRate", stockExposureRate);
        if (stockExposureRate.compareTo(requiredRate(
                overseasProperties.maxStockExposureRate(), "overseas maxStockExposureRate")) > 0) {
            reasons.add("Overseas max stock exposure rate exceeded: " + stockExposureRate);
            return blocked(RiskDecision.BLOCKED_BY_STOCK_EXPOSURE, reasons, snapshot);
        }

        if (properties.requireSectorData()) {
            if (command.sector() == null || command.sector().isBlank()
                    || "UNKNOWN".equalsIgnoreCase(command.sector())) {
                reasons.add("US sector data is required for entry risk evaluation.");
                return blocked(RiskDecision.BLOCKED_BY_INCOMPLETE_RISK_DATA, reasons, snapshot);
            }
            BigDecimal currentSectorExposure = BigDecimal.ZERO;
            for (OverseasPosition position : account.positions()) {
                String sector = stockSectorResolver.resolve(position.stockCode(), MarketType.OVERSEAS)
                        .sectorName();
                if (sector.equalsIgnoreCase(command.sector()) && position.evaluationAmount() != null) {
                    currentSectorExposure = currentSectorExposure.add(position.evaluationAmount());
                }
            }
            BigDecimal sectorExposureRate = currentSectorExposure.add(orderAmount)
                    .divide(totalAssetsUsd, 4, RoundingMode.HALF_UP);
            snapshot.put("sectorExposureRate", sectorExposureRate);
            if (sectorExposureRate.compareTo(properties.maxSectorExposureRate()) > 0) {
                reasons.add("Overseas max sector exposure rate exceeded: " + sectorExposureRate);
                return blocked(RiskDecision.BLOCKED_BY_SECTOR_EXPOSURE, reasons, snapshot);
            }
        }

        if (tradingLogRepository.existsOpenOrder(
                command.accountKey(), command.marketType(), command.stockCode(), command.orderType())) {
            reasons.add("Open overseas BUY order already exists for " + command.stockCode());
            return blocked(RiskDecision.BLOCKED_BY_DUPLICATE_ORDER, reasons, snapshot);
        }
        var tradingDay = tradingTimeService.currentMarketDay(MarketType.OVERSEAS);
        long dailyTradeCount = tradingLogRepository.countSubmittedOrders(
                command.accountKey(), MarketType.OVERSEAS,
                tradingDay.startInclusive(), tradingDay.endExclusive());
        snapshot.put("dailyTradeCount", dailyTradeCount);
        if (dailyTradeCount >= overseasProperties.maxDailyTrades()) {
            reasons.add("Overseas max daily trades exceeded: " + dailyTradeCount);
            return blocked(RiskDecision.BLOCKED_BY_MAX_DAILY_TRADES, reasons, snapshot);
        }
        return allowed(reasons, snapshot);
    }

    private BigDecimal requiredPositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(name + " must be positive.");
        }
        return value;
    }

    private BigDecimal requiredRate(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException(name + " must be greater than 0 and at most 1.");
        }
        return value;
    }

    private RiskEvaluationResult blocked(
            RiskDecision decision, List<String> reasons, Map<String, Object> snapshot) {
        return new RiskEvaluationResult(false, decision, reasons, snapshot);
    }

    private RiskEvaluationResult allowed(List<String> reasons, Map<String, Object> snapshot) {
        return new RiskEvaluationResult(true, RiskDecision.ALLOWED, reasons, snapshot);
    }
}
