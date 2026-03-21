package com.migration.servicelayer.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.MappingRequest;
import com.migration.servicelayer.dto.MigrationStartRequest;
import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.service.AIMappingService;
import com.migration.servicelayer.service.MappingStore;
import com.migration.servicelayer.service.MigrationService;
import com.migration.servicelayer.service.ProtocolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    private final MigrationService migrationService;
    private final AIMappingService aiMappingService;
    private final MappingStore mappingStore;
    private final ObjectMapper objectMapper;
    private final ProtocolService protocolService;

    public MigrationController(
            MigrationService migrationService,
            AIMappingService aiMappingService,
            MappingStore mappingStore,
            ObjectMapper objectMapper,
            ProtocolService protocolService
    ) {
        this.migrationService = migrationService;
        this.aiMappingService = aiMappingService;
        this.mappingStore = mappingStore;
        this.objectMapper = objectMapper;
        this.protocolService = protocolService;
    }

    @PostMapping("/ai-map")
    public ResponseEntity<String> generateAIMapping(
            @RequestBody MappingRequest request,
            @RequestParam String targetTable
    ) {
        try {
            String jsonMapping = aiMappingService.generateMapping(
                    request.originSchema(),
                    request.targetSchema()
            );
            Map<String, String> mapConfig = objectMapper.readValue(jsonMapping, new TypeReference<>() {});
            mappingStore.saveMapping(targetTable, mapConfig);
            return ResponseEntity.ok(jsonMapping);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao processar com a IA: " + e.getMessage());
        }
    }

    @GetMapping("/mapping/{targetTable}")
    public ResponseEntity<Map<String, String>> getMapping(@PathVariable String targetTable) {
        Map<String, String> mapping = mappingStore.getMapping(targetTable);
        if (mapping == null || mapping.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapping);
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> start(@RequestBody MigrationStartRequest request) {
        String protocolId = migrationService.startMassMigration(request.originTable(), request.targetTable());
        return ResponseEntity.accepted().body(Map.of(
                "protocolId", protocolId,
                "message", "Migração iniciada de " + request.originTable() + " para " + request.targetTable()
        ));
    }

    @GetMapping("/status/{protocolId}")
    public ResponseEntity<ProtocolResponse> status(@PathVariable String protocolId) {
        return ResponseEntity.ok(protocolService.getStatus(protocolId));
    }
}