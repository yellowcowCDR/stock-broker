package com.hermes.broker.market.application.service;

import com.hermes.broker.common.exception.DataPipelineUnavailableException;
import com.hermes.broker.market.application.port.in.GetUsFundamentalsUseCase;
import com.hermes.broker.market.application.port.out.LoadUsFundamentalsPort;
import com.hermes.broker.market.domain.UsFundamentalsSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UsFundamentalsService implements GetUsFundamentalsUseCase {

    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9.\\-]{1,20}");

    private final LoadUsFundamentalsPort loadUsFundamentalsPort;

    @Override
    public UsFundamentalsSnapshot getUsFundamentals(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid United States stock symbol: " + symbol);
        }
        UsFundamentalsSnapshot snapshot = loadUsFundamentalsPort.load(normalized);
        if (snapshot == null || !snapshot.financialDataComplete()) {
            throw new DataPipelineUnavailableException(
                    "United States financial data is incomplete for " + normalized + "."
            );
        }
        return snapshot;
    }
}
