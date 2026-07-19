package com.hermes.broker.common.monitoring.adapter.in.web;

import com.hermes.broker.common.monitoring.CronHeartbeat;
import com.hermes.broker.common.monitoring.CronHeartbeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/operations/cron-heartbeats")
@RequiredArgsConstructor
public class CronHeartbeatController {

    private final CronHeartbeatService service;

    @PostMapping
    public ResponseEntity<CronHeartbeat> record(@Valid @RequestBody CronHeartbeatRequest request) {
        return ResponseEntity.ok(service.record(
                request.cronName(), request.executionId(), request.phase(),
                request.expectedIntervalSeconds(), request.expectedNextAt(), request.message()));
    }
}
