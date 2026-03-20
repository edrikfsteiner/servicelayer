package com.migration.servicelayer.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.MigrationStartRequest;
import com.migration.servicelayer.service.MappingStore;
import com.migration.servicelayer.service.MigrationService;
import com.migration.servicelayer.dto.MapeamentoRequest;
import com.migration.servicelayer.service.AIMappingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    private final MigrationService migrationService;
    private final AIMappingService aiMappingService;
    private final MappingStore mappingStore;
    private final ObjectMapper objectMapper;

    public MigrationController(
            MigrationService migrationService,
            AIMappingService aiMappingService,
            MappingStore mappingStore,
            ObjectMapper objectMapper
    ) {
        this.migrationService = migrationService;
        this.aiMappingService = aiMappingService;
        this.mappingStore = mappingStore;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/ai-map")
    public ResponseEntity<String> generateAIMapping(
            @RequestBody MapeamentoRequest request,
            @RequestParam String targetTable
    ) {
        try {
            String jsonMapping = aiMappingService.generateMapping(
                    request.originSchema(),
                    request.targetSchema()
            );
            Map<String, String> mapConfig = objectMapper.readValue(jsonMapping, new TypeReference<>(){});
            mappingStore.saveMapping(targetTable, mapConfig);
            return ResponseEntity.ok("Mapeamento gerado e salvo com sucesso para a tabela: " + targetTable + "\n" + jsonMapping);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao processar com a IA: " + e.getMessage());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<String> start(@RequestBody MigrationStartRequest request) {
        migrationService.startMassMigration(request.originTable(), request.targetTable());
        return ResponseEntity.accepted().body("Processo de migração iniciado de " + request.originTable() + " para " + request.targetTable());
    }
}