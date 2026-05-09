package com.migration.servicelayer.dto;

import com.migration.servicelayer.model.IngestionProtocol;
import com.migration.servicelayer.model.ProtocolStatus;

import java.time.LocalDateTime;

public record ProtocolResponse(
        String protocolId,
        String tenantId,
        String eventType,
        ProtocolStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProtocolResponse toDto(IngestionProtocol protocol) {
        return new ProtocolResponse(
                protocol.getId(),
                protocol.getTenantId(),
                protocol.getEventType(),
                protocol.getStatus(),
                protocol.getCreatedAt(),
                protocol.getUpdatedAt()
        );
    }
}
