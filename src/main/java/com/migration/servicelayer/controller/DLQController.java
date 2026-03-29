package com.migration.servicelayer.controller;

import com.migration.servicelayer.service.DLQService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
public class DLQController {

    private final DLQService dlqService;

    public DLQController(DLQService dlqService) {
        this.dlqService = dlqService;
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok(Map.of("messagesInDlq", dlqService.count()));
    }

    @PostMapping("/reprocess")
    public ResponseEntity<Map<String, Integer>> reprocessAll() {
        int count = dlqService.reprocessAll();
        return ResponseEntity.ok(Map.of("reprocessed", count));
    }
}
