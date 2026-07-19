package com.hermes.broker.trading.application.service;

import com.hermes.broker.trading.application.port.in.CancelOrderUseCase;
import com.hermes.broker.trading.application.port.in.GetOpenOrdersUseCase;
import com.hermes.broker.trading.application.port.out.CancelOrderPort;
import com.hermes.broker.trading.application.port.out.LoadOpenOrdersPort;
import com.hermes.broker.trading.application.port.out.TradingLogRepository;
import com.hermes.broker.trading.domain.OrderStatus;
import com.hermes.broker.trading.domain.TradingLog;
import com.hermes.broker.trading.domain.MarketType;
import com.hermes.broker.trading.domain.portfolio.OpenOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderManagementService implements GetOpenOrdersUseCase, CancelOrderUseCase {

    private final List<LoadOpenOrdersPort> loadOpenOrdersPorts;
    private final List<CancelOrderPort> cancelOrderPorts;
    private final TradingLogRepository tradingLogRepository;
    private final TradingEnvironmentGuard environmentGuard;
    private final BrokerAccountKeyProvider accountKeyProvider;
    private final AccountLockService accountLockService;

    @Override
    public List<OpenOrder> getOpenOrders() {
        return loadOpenOrdersPorts.stream()
                .flatMap(port -> port.loadOpenOrders().stream())
                .toList();
    }

    @Override
    public void cancelOrder(String orderId, String stockCode, MarketType marketType, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required for cancellation.");
        }
        String accountKey = accountKeyProvider.getAccountKey();
        accountLockService.executeWithLock(accountKey, () -> {
            environmentGuard.validateCancellation();
            TradingLog order = tradingLogRepository.findByExternalOrderId(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Broker order not found: " + orderId));

            if (!accountKey.equals(order.getAccountKey())
                    || order.getMarketType() != marketType
                    || !order.getStockCode().equalsIgnoreCase(stockCode)) {
                throw new IllegalArgumentException("Order ownership or order attributes do not match.");
            }
            if (order.getCancelIdempotencyKey() != null) {
                if (!order.getCancelIdempotencyKey().equals(idempotencyKey)) {
                    throw new IllegalArgumentException("Cancellation already requested with another idempotency key.");
                }
                return null;
            }
            if (order.getStatus() != OrderStatus.SUBMITTED
                    && order.getStatus() != OrderStatus.PENDING
                    && order.getStatus() != OrderStatus.PARTIALLY_EXECUTED
                    && order.getStatus() != OrderStatus.UNKNOWN) {
                throw new IllegalStateException("Order is not cancellable in status " + order.getStatus());
            }

            CancelOrderPort cancelOrderPort = cancelOrderPorts.stream()
                    .filter(port -> port.supports(marketType))
                    .findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("Cancellation is not supported for " + marketType));

            int remainingQuantity = loadOpenOrdersPorts.stream()
                    .filter(port -> port.supports(marketType))
                    .findFirst()
                    .flatMap(port -> port.loadOpenOrders().stream()
                            .filter(open -> open.orderId().equals(orderId))
                            .findFirst())
                    .map(open -> open.quantity().subtract(open.executedQuantity()).intValueExact())
                    .orElse(order.getOrderQuantity());

            order.markCancelRequested(idempotencyKey);
            tradingLogRepository.save(order);
            try {
                cancelOrderPort.cancelOrder(
                        orderId, stockCode, marketType, order.getExchangeCode(), remainingQuantity);
                order.markCancellationAccepted("Cancellation request accepted by KIS; final state requires reconciliation.");
                tradingLogRepository.save(order);
            } catch (Exception e) {
                order.markUnknown("Cancellation result is ambiguous: " + e.getMessage());
                tradingLogRepository.save(order);
                throw e;
            }
            return null;
        });
    }
}
