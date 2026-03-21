package com.migration.servicelayer.dto;

public record MappingRequest(
        String originSchema,
        String targetSchema
) {}