package com.migration.servicelayer.controller;

import com.migration.servicelayer.service.TransformationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transformation")
public class TransformationController {

    private final TransformationScheduler scheduler;

    @PostMapping("/run")
    public ResponseEntity<String> forceRun() {
        scheduler.processAllTenants();
        return ResponseEntity.accepted().body("Transformação de dados iniciada.");
    }
}
