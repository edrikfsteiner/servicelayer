package com.migration.servicelayer.controller;

import com.migration.servicelayer.dto.SchemaFieldRule;
import com.migration.servicelayer.model.SchemaMappingRules;
import com.migration.servicelayer.repository.SchemaMappingRulesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final SchemaMappingRulesRepository repository;

    @PostMapping("/{eventType}")
    public ResponseEntity<SchemaMappingRules> registerSchema(
            @PathVariable String eventType,
            @RequestBody List<SchemaFieldRule> request,
            JwtAuthenticationToken token
    ) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");

        if (repository.findByTenantIdAndEventType(tenantId, eventType).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    String.format("Schema já existe para tenantId='%s', eventType='%s'.", tenantId, eventType)
            );
        }

        validateFields(request);

        SchemaMappingRules rules = SchemaMappingRules.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .fields(request)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rules));
    }

    @PutMapping("/{eventType}")
    public ResponseEntity<SchemaMappingRules> updateSchema(
            @PathVariable String eventType,
            @RequestBody List<SchemaFieldRule> request,
            JwtAuthenticationToken token
    ) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");

        SchemaMappingRules existing = repository.findByTenantIdAndEventType(tenantId, eventType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        String.format("Schema não encontrado para tenantId='%s', eventType='%s'.", tenantId, eventType)
                ));

        validateFields(request);

        existing.setFields(request);
        existing.setUpdatedAt(LocalDateTime.now());

        return ResponseEntity.ok(repository.save(existing));
    }

    @GetMapping
    public ResponseEntity<List<SchemaMappingRules>> listSchemas(JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        return ResponseEntity.ok(repository.findByTenantId(tenantId));
    }

    @GetMapping("/{eventType}")
    public ResponseEntity<SchemaMappingRules> getSchema(@PathVariable String eventType, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        return ResponseEntity.ok(
                repository.findByTenantIdAndEventType(tenantId, eventType)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Schema não encontrado para eventType='" + eventType + "'."
                        ))
        );
    }

    @DeleteMapping("/{eventType}")
    public ResponseEntity<String> deleteSchema(@PathVariable String eventType, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");

        SchemaMappingRules existing = repository.findByTenantIdAndEventType(tenantId, eventType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Schema não encontrado para eventType='" + eventType + "'."
                ));

        repository.delete(existing);

        return ResponseEntity.ok("Schema '" + eventType + "' removido com sucesso.");
    }

    private void validateFields(List<SchemaFieldRule> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lista de fields não pode ser vazia.");
        }
        if (fields.stream().anyMatch(field -> field.fieldName() == null || field.fieldName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldName não pode ser vazio.");
        }
    }
}