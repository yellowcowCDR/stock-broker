package com.hermes.broker.trading.adapter.in.scheduler;

import com.hermes.broker.common.monitoring.OperationalEventRecorder;
import com.hermes.broker.common.time.TradingTimeService;
import com.hermes.broker.trading.adapter.out.persistence.TradingLogJpaRepository;
import com.hermes.broker.trading.application.port.out.LoadOrderExecutionsPort;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.OrderExecutionSnapshot;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverseasOrderPollingScheduler {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final TradingLogJpaRepository tradingLogRepository;
    private final List<LoadOrderExecutionsPort> executionPorts;
    private final TradingTimeService tradingTimeService;
    private final OperationalEventRecorder operationalEventRecorder;

    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void pollPendingOrders() {
        List<TradingLog> pending = tradingLogRepository.findAll().stream()
                .filter(order -> order.getMarketType() == MarketType.OVERSEAS)
                .filter(order -> order.getExternalOrderId() != null)
                .filter(order -> order.getStatus() == OrderStatus.SUBMITTED
                        || order.getStatus() == OrderStatus.PENDING
                        || order.getStatus() == OrderStatus.PARTIALLY_EXECUTED
                        || order.getStatus() == OrderStatus.CANCEL_REQUESTED
                        || order.getStatus() == OrderStatus.UNKNOWN)
                .toList();
        if (pending.isEmpty()) {
            return;
        }

        LoadOrderExecutionsPort port = executionPorts.stream()
                .filter(candidate -> candidate.supports(MarketType.OVERSEAS))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No overseas execution reconciliation provider is configured."));
        LocalDate to = tradingTimeService.currentMarketDate(MarketType.OVERSEAS);
        LocalDate from = pending.stream()
                .map(order -> order.getCreatedAt().atZone(NEW_YORK).toLocalDate())
                .min(Comparator.naturalOrder())
                .orElse(to);
        if (from.isAfter(to)) {
            from = to;
        }

        try {
            List<OrderExecutionSnapshot> snapshots = port.loadOrderExecutions(from, to);
            for (TradingLog order : pending) {
                reconcile(order, snapshots);
            }
        } catch (Exception failure) {
            operationalEventRecorder.recordReconciliation(false,
                    "Overseas execution polling failed: " + failure.getMessage());
            log.error("Failed to poll overseas execution status", failure);
        }
    }

    private void reconcile(TradingLog order, List<OrderExecutionSnapshot> snapshots) {
        String orderNo = externalOrderNumber(order.getExternalOrderId());
        List<OrderExecutionSnapshot> matches = snapshots.stream()
                .filter(snapshot -> orderNo.equals(snapshot.orderId())
                        || orderNo.equals(snapshot.originalOrderId()))
                .filter(snapshot -> snapshot.stockCode() == null || snapshot.stockCode().isBlank()
                        || snapshot.stockCode().equalsIgnoreCase(order.getStockCode()))
                .toList();
        if (matches.isEmpty()) {
            return;
        }

        if (matches.stream().anyMatch(OrderExecutionSnapshot::rejected)) {
            String message = matches.stream().filter(OrderExecutionSnapshot::rejected)
                    .map(OrderExecutionSnapshot::message)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst().orElse("KIS overseas order was rejected after submission.");
            order.markRejected(message);
            tradingLogRepository.save(order);
            operationalEventRecorder.recordReconciliation(true, null);
            return;
        }

        BigDecimal executedQuantity = matches.stream()
                .map(OrderExecutionSnapshot::executedQuantity)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal executionPrice = matches.stream()
                .filter(snapshot -> snapshot.executedQuantity() != null
                        && snapshot.executedQuantity().signum() > 0)
                .map(OrderExecutionSnapshot::executionPrice)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean canceled = matches.stream().anyMatch(OrderExecutionSnapshot::canceled);
        BigDecimal orderedQuantity = BigDecimal.valueOf(order.getOrderQuantity());

        if (executedQuantity.signum() > 0) {
            order.updateExecution(executionPrice, executedQuantity);
        }
        if (executedQuantity.compareTo(orderedQuantity) >= 0) {
            order.updateStatus(OrderStatus.EXECUTED);
        } else if (canceled && executedQuantity.signum() > 0) {
            order.updateStatus(OrderStatus.PARTIALLY_EXECUTED_CANCELED);
        } else if (canceled) {
            order.markCanceled("KIS overseas cancellation confirmed.");
            order.reconcileNoExecution("KIS_INQUIRE_CCNL:NO_EXECUTION", java.time.Instant.now());
        } else if (executedQuantity.signum() > 0) {
            order.updateStatus(OrderStatus.PARTIALLY_EXECUTED);
        } else {
            return;
        }
        tradingLogRepository.save(order);
        operationalEventRecorder.recordReconciliation(true, null);
        log.info("Matched KIS overseas execution to broker order {} ({}/{})",
                order.getId(), executedQuantity, orderedQuantity);
    }

    private String externalOrderNumber(String externalOrderId) {
        int separator = externalOrderId.indexOf('-');
        return separator < 0 ? externalOrderId : externalOrderId.substring(separator + 1);
    }
}
