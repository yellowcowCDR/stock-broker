package com.hermes.broker.summary.application.service;

import com.hermes.broker.summary.application.port.in.GetTradingReflectionUseCase;
import com.hermes.broker.summary.application.port.out.LoadTradingReflectionPort;
import com.hermes.broker.summary.domain.TradingReflection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingReflectionService implements GetTradingReflectionUseCase {

    private final LoadTradingReflectionPort loadTradingReflectionPort;

    @Override
    public List<TradingReflection> getReflectionsByDate(String date) {
        return loadTradingReflectionPort.loadByTradingDate(LocalDate.parse(date));
    }
}
