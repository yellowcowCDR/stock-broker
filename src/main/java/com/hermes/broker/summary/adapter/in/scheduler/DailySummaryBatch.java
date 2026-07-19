package com.hermes.broker.summary.adapter.in.scheduler;

import com.hermes.broker.summary.application.port.in.GenerateDailySummaryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySummaryBatch {

    private final GenerateDailySummaryUseCase generateDailySummaryUseCase;

    /**
     * 평일(월-금) 오후 4시에 장 마감 회고 데이터를 가공 및 적재합니다.
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Seoul")
    public void generateDailySummary() {
        generateDailySummaryUseCase.generateDailySummary();
    }
}
