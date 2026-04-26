package com.migration.servicelayer.dto;

import com.migration.servicelayer.model.BronzeDocument;

import java.util.List;

public record TransformationBatchMessage(
        String tenantId,
        String eventType,
        List<BronzeDocument> bronzeDocuments
) {}