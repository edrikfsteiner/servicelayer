package com.migration.servicelayer.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.MappingRequest;
import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.AIMappingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private final AIMappingService aiMappingService;
    private final MappingStore mappingStore;
    private final ObjectMapper objectMapper;
    private final ProtocolService protocolService;

    public IngestionController(
            IngestionService ingestionService,
            AIMappingService aiMappingService,
            MappingStore mappingStore,
            ObjectMapper objectMapper,
            ProtocolService protocolService
    ) {
        this.ingestionService = ingestionService;
        this.aiMappingService = aiMappingService;
        this.mappingStore = mappingStore;
        this.objectMapper = objectMapper;
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

    @PostMapping("/ai-map")
    public ResponseEntity<String> generateAIMapping(@RequestBody MappingRequest request, @RequestParam String lakehouseTable) {
        try {
            String jsonMapping = aiMappingService.generateMapping(
                    request.payloadJsonSample(),
                    request.lakehouseSchema()
            );
            Map<String, String> mapConfig = objectMapper.readValue(jsonMapping, new TypeReference<>() {});
            mappingStore.saveMapping(lakehouseTable, mapConfig);
            return ResponseEntity.ok(jsonMapping);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao processar com a IA: " + e.getMessage());
        }
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