package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.property.RiskPolicyProperties;
import com.hermes.broker.trading.application.port.in.EvaluateOrderRiskUseCase;
import com.hermes.broker.trading.application.port.in.GetPortfolioSummaryUseCase;
import com.hermes.broker.trading.application.port.in.OrderRiskCommand;
import com.hermes.broker.trading.application.port.out.LoadOpenOrdersPort;
import com.hermes.broker.trading.domain.OrderType;
import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import com.hermes.broker.trading.domain.portfolio.PortfolioPosition;
import com.hermes.broker.trading.domain.portfolio.PortfolioSummary;
import com.hermes.broker.trading.domain.portfolio.SectorExposure;
import com.hermes.broker.trading.domain.risk.RiskDecision;
import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private final LoadOpenOrdersPort loadOpenOrdersPort;

    @Override
    public RiskEvaluationResult evaluate(OrderRiskCommand command) {
        List<String> reasons = new ArrayList<>();
        Map<String, Object> snapshot = new HashMap<>();

        if (properties.killSwitchEnabled()) {
            reasons.add("Kill switch is active.");
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_KILL_SWITCH, reasons, snapshot);
        }

        if (command.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("Invalid quantity: " + command.quantity());
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INVALID_QUANTITY, reasons, snapshot);
        }

        // 매도 주문의 경우, 리스크 차단보다는 보유 수량 검증 위주로 처리
        if (command.orderType() == OrderType.SELL) {
            // (구현) 보유 수량 확인 로직
            return new RiskEvaluationResult(true, RiskDecision.ALLOWED, reasons, snapshot);
        }

        // --- 여기서부터는 BUY 주문에 대한 검증 ---

        PortfolioSummary portfolio = getPortfolioSummaryUseCase.getPortfolioSummary();
        List<OpenOrder> openOrders = loadOpenOrdersPort.loadOpenOrders();
        
        snapshot.put("totalAssetAmount", portfolio.totalAssetAmount());
        snapshot.put("dailyProfitLossAmount", portfolio.dailyProfitLossAmount());
        snapshot.put("positionCount", portfolio.positionCount());
        snapshot.put("buyingPower", portfolio.buyingPower());
        
        BigDecimal orderAmount = command.price().multiply(command.quantity());
        snapshot.put("orderAmount", orderAmount);

        // 1. 최대 주문 금액
        if (orderAmount.compareTo(properties.maxOrderAmount()) > 0) {
            reasons.add("Order amount exceeds max limit: " + orderAmount);
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_MAX_ORDER_AMOUNT, reasons, snapshot);
        }

        // 2. 주문 가능 잔고 확인
        if (orderAmount.compareTo(portfolio.buyingPower()) > 0 && !properties.allowMarginTrading()) {
            reasons.add("Insufficient buying power. Available: " + portfolio.buyingPower());
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_INSUFFICIENT_BALANCE, reasons, snapshot);
        }

        // 3. 일일 최대 손실률
        BigDecimal dailyLossRate = BigDecimal.ZERO;
        if (portfolio.totalAssetAmount().compareTo(BigDecimal.ZERO) > 0) {
            // dailyProfitLossAmount가 음수일 때 손실률 계산
            if (portfolio.dailyProfitLossAmount().compareTo(BigDecimal.ZERO) < 0) {
                dailyLossRate = portfolio.dailyProfitLossAmount().abs()
                        .divide(portfolio.totalAssetAmount(), 4, RoundingMode.HALF_UP);
            }
        }
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
        
        BigDecimal projectedTotalAsset = portfolio.totalAssetAmount().add(orderAmount);
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

        // 8. 중복 주문 확인 (동일 종목 미체결 BUY 존재 시)
        boolean duplicateOpenOrder = openOrders.stream()
                .anyMatch(o -> o.stockCode().equals(command.stockCode()) && o.orderType() == OrderType.BUY);
        if (duplicateOpenOrder) {
            reasons.add("Open BUY order already exists for " + command.stockCode());
            return new RiskEvaluationResult(false, RiskDecision.BLOCKED_BY_DUPLICATE_ORDER, reasons, snapshot);
        }

        // 9. 최대 일일 거래 횟수 초과 여부는 LoadTradingDecisionPort에서 당일 발생한 주문 건수를 통해 계산해야 함.
        // 현재는 포트폴리오/잔고만으로 확인 불가능하므로, Decision을 다루는 서비스 또는 포트에서 해결하거나
        // RiskService에서 `LoadTradingDecisionPort.countOrdersByTradingDate`를 사용하게 해야 함. 
        // 일단 패스.

        return new RiskEvaluationResult(true, RiskDecision.ALLOWED, reasons, snapshot);
    }
}
