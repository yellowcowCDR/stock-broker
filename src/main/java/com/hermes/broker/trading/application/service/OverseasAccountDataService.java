package com.hermes.broker.trading.application.service;

import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.trading.application.port.in.GetOverseasAccountDataUseCase;
import com.hermes.broker.trading.application.port.out.LoadOverseasAccountDataPort;
import com.hermes.broker.trading.domain.portfolio.OverseasAccountSnapshot;
import com.hermes.broker.trading.domain.portfolio.OverseasOrderCapacity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OverseasAccountDataService implements GetOverseasAccountDataUseCase {

    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9.\\-]{1,20}");
    private static final Set<String> US_EXCHANGES = Set.of("NASD", "NAS", "NYSE", "AMEX");

    private final LoadOverseasAccountDataPort loadOverseasAccountDataPort;

    @Override
    public OverseasAccountSnapshot getUnitedStatesAccount() {
        OverseasAccountSnapshot snapshot = loadOverseasAccountDataPort.loadUnitedStatesAccount();
        if (snapshot == null || !snapshot.complete() || snapshot.cashBalance() == null
                || snapshot.availableForUse() == null || snapshot.fetchedAt() == null) {
            throw new DataPipelineUnavailableException(
                    "KIS United States account snapshot is incomplete."
            );
        }
        return snapshot;
    }

    @Override
    public OverseasOrderCapacity getOrderCapacity(
            String stockCode,
            String exchangeCode,
            BigDecimal orderPrice
    ) {
        String normalizedStockCode = normalizeStockCode(stockCode);
        String normalizedExchange = normalizeExchange(exchangeCode);
        if (orderPrice == null || orderPrice.signum() <= 0) {
            throw new IllegalArgumentException("orderPrice must be positive");
        }

        OverseasOrderCapacity capacity = loadOverseasAccountDataPort.loadOrderCapacity(
                normalizedStockCode, normalizedExchange, orderPrice);
        if (capacity == null || !capacity.complete()
                || capacity.orderableForeignAmount() == null
                || capacity.maximumOrderableQuantity() == null
                || capacity.fetchedAt() == null) {
            throw new DataPipelineUnavailableException(
                    "KIS overseas order capacity is incomplete for " + normalizedStockCode + "."
            );
        }
        return capacity;
    }

    private String normalizeStockCode(String stockCode) {
        String normalized = stockCode == null
                ? "" : stockCode.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid United States stock code: " + stockCode);
        }
        return normalized;
    }

    private String normalizeExchange(String exchangeCode) {
        String normalized = exchangeCode == null
                ? "" : exchangeCode.trim().toUpperCase(Locale.ROOT);
        if (!US_EXCHANGES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "exchangeCode must be one of NASD, NAS, NYSE, AMEX"
            );
        }
        return normalized;
    }
}
