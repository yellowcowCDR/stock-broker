package com.hermes.broker.summary.application.service;

import com.hermes.broker.summary.application.port.in.GetTradingReflectionUseCase;
import com.hermes.broker.summary.domain.TradingReflection;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class TradingReflectionService implements GetTradingReflectionUseCase {

    @Override
    public List<TradingReflection> getReflectionsByDate(String date) {
        // Mock implementation
        return Collections.emptyList();
    }
}
