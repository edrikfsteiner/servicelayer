package com.migration.servicelayer.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record IngestionMessage(
        String protocolId,
        String tenantId,
        String eventType,
        JsonNode payload
) {}