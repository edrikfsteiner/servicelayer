package com.migration.servicelayer.dto;

import java.util.List;

public record TransformationBatchMessage(
        String tenantId,
        String eventType,
        List<String> bronzeIds
) {}