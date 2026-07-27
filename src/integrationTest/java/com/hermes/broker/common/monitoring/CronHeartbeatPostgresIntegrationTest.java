package com.hermes.broker.common.monitoring;

import com.hermes.broker.common.exception.CronExecutionConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"mock", "local"})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.task.scheduling.enabled=false"
)
class CronHeartbeatPostgresIntegrationTest {

    private static final String HEARTBEAT_PATH =
            "/api/v1/internal/operations/cron-heartbeats";

    @Autowired
    CronHeartbeatService heartbeatService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void hibernateCreatesLeaseColumnAndLegacyStartedRowCanBeTakenOver() {
        Integer leaseColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'cron_heartbeat'
                  AND column_name = 'lease_expires_at'
                """, Integer.class);
        assertThat(leaseColumnCount).isEqualTo(1);

        Instant now = Instant.now();
        Instant legacyStartedAt = now.minus(Duration.ofMinutes(31));
        Instant nextSlot = now.plus(Duration.ofHours(12));
        jdbcTemplate.update("""
                        INSERT INTO cron_heartbeat (
                            cron_name,
                            execution_id,
                            phase,
                            expected_interval_seconds,
                            last_started_at,
                            last_completed_at,
                            lease_expires_at,
                            expected_next_at,
                            message,
                            updated_at,
                            row_version
                        ) VALUES (?, ?, 'STARTED', ?, ?, NULL, NULL, ?, ?, ?, 0)
                        """,
                "krx-paper-cycle1",
                "legacy-stuck-run",
                Duration.between(legacyStartedAt, nextSlot).getSeconds(),
                Timestamp.from(legacyStartedAt),
                Timestamp.from(nextSlot),
                "legacy started",
                Timestamp.from(legacyStartedAt));

        CronHeartbeat takeover = heartbeatService.record(
                "krx-paper-cycle1",
                "replacement-run",
                CronHeartbeatPhase.STARTED,
                null,
                nextSlot,
                "replacement started");

        assertThat(takeover.executionId()).isEqualTo("replacement-run");
        assertThat(takeover.phase()).isEqualTo(CronHeartbeatPhase.STARTED);
        assertThat(takeover.leaseExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(29)));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT lease_expires_at IS NOT NULL
                FROM cron_heartbeat
                WHERE cron_name = 'krx-paper-cycle1'
                """, Boolean.class)).isTrue();
    }

    @Test
    void activeLeaseConflictIsReturnedAsHttp409() {
        Instant nextSlot = Instant.now().plus(Duration.ofHours(12));
        heartbeatService.record(
                "krx-paper-cycle1",
                "active-run",
                CronHeartbeatPhase.STARTED,
                null,
                nextSlot,
                "started");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Actor", "integration-test");
        headers.set("X-Correlation-ID", "replacement-run");
        Map<String, Object> payload = Map.of(
                "cronName", "krx-paper-cycle1",
                "executionId", "replacement-run",
                "phase", "STARTED",
                "expectedNextAt", nextSlot.toString(),
                "message", "started");

        ResponseEntity<String> response = restTemplate.postForEntity(
                HEARTBEAT_PATH,
                new HttpEntity<>(payload, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .contains("active-run")
                .contains("leaseExpiresAt");
    }

    @Test
    void sameStartedHeartbeatRenewsLeaseInPostgres() {
        Instant nextSlot = Instant.now().plus(Duration.ofHours(12));
        CronHeartbeat started = heartbeatService.record(
                "krx-paper-cycle1",
                "renewed-run",
                CronHeartbeatPhase.STARTED,
                null,
                nextSlot,
                "started");
        Instant shortenedLease = Instant.now().plus(Duration.ofMinutes(1));
        jdbcTemplate.update("""
                        UPDATE cron_heartbeat
                        SET lease_expires_at = ?
                        WHERE cron_name = 'krx-paper-cycle1'
                        """,
                Timestamp.from(shortenedLease));

        CronHeartbeat renewed = heartbeatService.record(
                "krx-paper-cycle1",
                "renewed-run",
                CronHeartbeatPhase.STARTED,
                null,
                nextSlot,
                "started");

        assertThat(renewed.lastStartedAt()).isEqualTo(started.lastStartedAt());
        assertThat(renewed.leaseExpiresAt())
                .isAfter(Instant.now().plus(Duration.ofMinutes(29)));
        assertThat(renewed.leaseExpiresAt()).isAfter(shortenedLease);
    }

    @Test
    void postgresRowLockAllowsOnlyOneConcurrentExecutionToStart() throws Exception {
        Instant nextSlot = Instant.now().plus(Duration.ofHours(12));
        heartbeatService.record(
                "krx-paper-cycle1",
                "seed-run",
                CronHeartbeatPhase.SUCCEEDED,
                null,
                nextSlot,
                "seed completed");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<StartAttempt> first = executor.submit(
                    () -> startAttempt("concurrent-run-1", nextSlot, ready, start));
            Future<StartAttempt> second = executor.submit(
                    () -> startAttempt("concurrent-run-2", nextSlot, ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get().success() ^ second.get().success()).isTrue();
            assertThat(first.get().conflict() ^ second.get().conflict()).isTrue();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM cron_heartbeat
                    WHERE cron_name = 'krx-paper-cycle1'
                      AND phase = 'STARTED'
                    """, Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private StartAttempt startAttempt(
            String executionId,
            Instant nextSlot,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            heartbeatService.record(
                    "krx-paper-cycle1",
                    executionId,
                    CronHeartbeatPhase.STARTED,
                    null,
                    nextSlot,
                    "started");
            return new StartAttempt(true, false);
        } catch (CronExecutionConflictException expected) {
            return new StartAttempt(false, true);
        }
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM api_call_log");
        jdbcTemplate.update("DELETE FROM cron_heartbeat");
    }

    private record StartAttempt(boolean success, boolean conflict) {
    }
}
