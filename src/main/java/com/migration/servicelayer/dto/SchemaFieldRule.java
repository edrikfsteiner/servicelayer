package com.migration.servicelayer.dto;

import com.migration.servicelayer.model.FieldType;

public record SchemaFieldRule(
        String fieldName,
        FieldType fieldType,
        boolean trim,
        boolean nullable
) {}
