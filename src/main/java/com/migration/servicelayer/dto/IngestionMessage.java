package com.migration.servicelayer.dto;

import java.util.Map;

public record IngestionMessage(
        String protocolId,
        String tenantId,
        String eventType,
        Map<String, Object> payload
) {}