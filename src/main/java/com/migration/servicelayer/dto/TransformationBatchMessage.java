package com.migration.servicelayer.dto;

import com.migration.servicelayer.model.BronzeDocument;
import com.migration.servicelayer.model.SchemaMappingRules;

import java.util.List;

public record TransformationBatchMessage(
        SchemaMappingRules schema,
        List<BronzeDocument> bronzeDocuments
) {}