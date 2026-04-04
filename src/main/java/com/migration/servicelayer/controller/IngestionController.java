package com.migration.servicelayer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.IngestionService;
import com.migration.servicelayer.service.MappingStore;
import com.migration.servicelayer.service.ProtocolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private final MappingStore mappingStore;
    private final ProtocolService protocolService;

    public IngestionController(
            IngestionService ingestionService,
            MappingStore mappingStore,
            ProtocolService protocolService
    ) {
        this.ingestionService = ingestionService;
        this.mappingStore = mappingStore;
        this.protocolService = protocolService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receiveData(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Event-Type", defaultValue = "raw_data") String eventType,
            @RequestBody JsonNode payload
    ) {
        return ResponseEntity.accepted().body(Map.of(
                "protocolId", ingestionService.publishToQueue(tenantId, eventType, payload),
                "status", ProtocolStatus.QUEUED.toString(),
                "message", "Dado recebido e enfileirado para processamento."
        ));
    }

    @GetMapping("/mapping/{lakehouseTable}")
    public ResponseEntity<Map<String, String>> getMapping(@PathVariable String lakehouseTable) {
        Map<String, String> mapping = mappingStore.getMapping(lakehouseTable);
        if (mapping == null || mapping.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapping);
    }

    @GetMapping("/status/{protocolId}")
    public ResponseEntity<ProtocolResponse> status(@PathVariable String protocolId) {
        return ResponseEntity.ok(protocolService.getStatus(protocolId));
    }
}