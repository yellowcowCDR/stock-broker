package com.hermes.broker.common.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OperationalStatusEvaluator {

    private final OperationalStatusService statusService;

    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void refreshAlertGauges() {
        statusService.getStatus();
    }
}
