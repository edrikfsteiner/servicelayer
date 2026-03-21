package com.migration.servicelayer.dto;

import com.migration.servicelayer.model.ProtocolStatus;

import java.time.LocalDateTime;

public record ProtocolResponse(
        String protocolId,
        String originTable,
        String targetTable,
        long totalRecords,
        long processedRecords,
        long failedRecords,
        ProtocolStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
