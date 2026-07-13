package com.hermes.broker.summary.application.service;

import com.hermes.broker.summary.application.port.in.RunDailyReflectionUseCase;
import com.hermes.broker.summary.application.port.out.SaveTradingReflectionPort;
import com.hermes.broker.summary.domain.TradeReview;
import com.hermes.broker.summary.domain.TradingReflection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReflectionService implements RunDailyReflectionUseCase {

    private final SaveTradingReflectionPort saveTradingReflectionPort;

    @Override
    public void runDailyReflection() {
        log.info("Starting daily reflection...");

        // (실제로는 LoadTradingDecisionPort, Portfolio 등에서 당일 데이터를 가져와야 함)
        List<TradeReview> reviews = new ArrayList<>();
        reviews.add(new TradeReview(
                "005930",
                UUID.randomUUID().toString(),
                "BUY",
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                true,
                "The actual return was lower than expected, possibly due to market volatility."
        ));

        TradingReflection reflection = new TradingReflection(
                UUID.randomUUID().toString(),
                LocalDate.now(),
                "v1.0.0-dummy",
                new BigDecimal("0.015"),
                new BigDecimal("-0.005"),
                reviews,
                "Overall performance was decent despite a market downturn. The model successfully identified a defensive stock.",
                "Need to refine volatility thresholds during market drops.",
                LocalDateTime.now()
        );

        saveTradingReflectionPort.save(reflection);
        log.info("Daily reflection saved for {}", reflection.tradingDate());
    }
}
