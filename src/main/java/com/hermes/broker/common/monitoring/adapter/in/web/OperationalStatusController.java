package com.hermes.broker.common.monitoring.adapter.in.web;

import com.hermes.broker.common.monitoring.OperationalStatus;
import com.hermes.broker.common.monitoring.OperationalStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/operations")
@RequiredArgsConstructor
public class OperationalStatusController {

    private final OperationalStatusService statusService;

    @GetMapping("/status")
    public ResponseEntity<OperationalStatus> getStatus() {
        return ResponseEntity.ok(statusService.getStatus());
    }
}
