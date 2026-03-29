package com.migration.servicelayer.dto;

import com.migration.servicelayer.model.ProtocolStatus;

import java.time.LocalDateTime;

public record ProtocolResponse(
        String protocolId,
        String tenantId,
        String eventType,
        ProtocolStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
