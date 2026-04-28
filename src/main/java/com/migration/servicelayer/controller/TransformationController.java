package com.migration.servicelayer.controller;

import com.migration.servicelayer.service.TransformationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transformation")
public class TransformationController {

    private final TransformationService service;

    @PostMapping("/run")
    public ResponseEntity<String> forceRun() {
        service.runAsync();
        return ResponseEntity.accepted().body("Transformacao de dados iniciada.");
    }
}
