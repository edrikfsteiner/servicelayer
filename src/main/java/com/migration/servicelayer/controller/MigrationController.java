package com.migration.servicelayer.controller;

import com.migration.servicelayer.service.MigrationService;
import com.migration.servicelayer.dto.MapeamentoRequest;
import com.migration.servicelayer.service.AIMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    @Autowired
    private final MigrationService migrationService;

    private final AIMappingService aiMappingService;

    public MigrationController(MigrationService migrationService, AIMappingService aiMappingService) {
        this.migrationService = migrationService;
        this.aiMappingService = aiMappingService;
    }

    @PostMapping("/ai-map")
    public ResponseEntity<String> generateAIMapping(@RequestBody MapeamentoRequest request) {
        try {
            String jsonMapping = aiMappingService.generateMapping(
                    request.originSchema(),
                    request.targetSchema()
            );

            return ResponseEntity.ok(jsonMapping);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao processar com a IA: " + e.getMessage());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<String> start() {
        migrationService.startMassMigration();
        return ResponseEntity.ok("Processo de migração enviado para a fila.");
    }
}