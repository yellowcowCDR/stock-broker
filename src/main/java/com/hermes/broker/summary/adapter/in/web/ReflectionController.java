package com.hermes.broker.summary.adapter.in.web;

import com.hermes.broker.summary.application.port.in.RunDailyReflectionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/broker/summary/reflection")
@RequiredArgsConstructor
public class ReflectionController {

    private final RunDailyReflectionUseCase runDailyReflectionUseCase;

    @PostMapping("/run")
    public ResponseEntity<Void> runDailyReflection() {
        runDailyReflectionUseCase.runDailyReflection();
        return ResponseEntity.ok().build();
    }
}
