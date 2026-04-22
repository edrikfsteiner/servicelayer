package com.migration.servicelayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.SchemaMappingRulesResponse;
import com.migration.servicelayer.model.SchemaMappingRules;
import com.migration.servicelayer.repository.SchemaMappingRulesRepository;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.migration.servicelayer.dto.SchemaMappingRulesResponse.toDto;

@RequiredArgsConstructor
@Service
public class SchemaService {

    private final SchemaMappingRulesRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    public SchemaMappingRulesResponse registerSchema(String eventType, Map<String, Object> fields, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");

        if (repository.findByTenantIdAndEventType(tenantId, eventType).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    String.format("Esquema já existe para tenantId='%s', eventType='%s'.", tenantId, eventType)
            );
        }

        validateFields(fields);

        SchemaMappingRules schema = repository.save(
                SchemaMappingRules.builder()
                        .tenantId(tenantId)
                        .eventType(eventType)
                        .fields(fields)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        return toDto(schema);
    }

    public SchemaMappingRulesResponse updateSchema(String eventType, Map<String, Object> fields, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");

        SchemaMappingRules schema = repository.findByTenantIdAndEventType(tenantId, eventType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        String.format("Esquema não encontrado para tenantId='%s', eventType='%s'.", tenantId, eventType)
                ));

        validateFields(fields);

        schema.setFields(fields);
        schema.setUpdatedAt(LocalDateTime.now());

        return toDto(schema);
    }

    public List<SchemaMappingRulesResponse> listSchemas(JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        List<SchemaMappingRules> schemas = repository.findByTenantId(tenantId);
        return schemas.stream().map(SchemaMappingRulesResponse::toDto).toList();
    }

    public SchemaMappingRulesResponse getSchema(String eventType, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        SchemaMappingRules schema = repository.findByTenantIdAndEventType(tenantId, eventType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Schema não encontrado para eventType='" + eventType + "'."
                ));
        return toDto(schema);
    }

    public void deleteSchema(String eventType, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");

        SchemaMappingRules schema = repository.findByTenantIdAndEventType(tenantId, eventType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Esquema não encontrado para eventType='" + eventType + "'."
                ));

        repository.delete(schema);
    }

    // ignorado retorno do getSchema(), pois ele serve apenas para validar
    private void validateFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os campos não podem estar vazios.");
        }
        schemaFactory.getSchema(objectMapper.valueToTree(fields));
    }
}
