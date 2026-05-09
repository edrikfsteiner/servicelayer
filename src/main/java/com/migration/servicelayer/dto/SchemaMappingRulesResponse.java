package com.migration.servicelayer.dto;

import com.migration.servicelayer.model.SchemaMappingRules;

import java.time.LocalDateTime;
import java.util.Map;

public record SchemaMappingRulesResponse(
        String tenantId,
        String eventType,
        Map<String, Object> fields,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SchemaMappingRulesResponse toDto(SchemaMappingRules rules) {
        return new SchemaMappingRulesResponse(
                rules.getTenantId(),
                rules.getEventType(),
                rules.getFields(),
                rules.getCreatedAt(),
                rules.getUpdatedAt()
        );
    }
}
