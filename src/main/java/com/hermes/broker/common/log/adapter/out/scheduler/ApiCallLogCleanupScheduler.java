package com.hermes.broker.common.log.adapter.out.scheduler;

import com.hermes.broker.common.log.adapter.out.persistence.ApiCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiCallLogCleanupScheduler {

    private final ApiCallLogRepository apiCallLogRepository;

    /**
     * 매일 자정(00:00)에 실행되어 1개월이 지난 API 호출 로그를 삭제합니다.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupOldApiLogs() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        log.info("Starting cleanup for API call logs created before: {}", oneMonthAgo);

        try {
            int deletedCount = apiCallLogRepository.deleteByCreatedAtBefore(oneMonthAgo);
            log.info("Successfully deleted {} old API call logs.", deletedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup old API call logs", e);
        }
    }
}
