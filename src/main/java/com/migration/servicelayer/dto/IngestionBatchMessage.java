package com.migration.servicelayer.dto;

import java.util.List;
import java.util.Map;

public record IngestionBatchMessage(
        String protocolId,
        String tenantId,
        String eventType,
        List<Map<String, Object>> payloads
) {}

