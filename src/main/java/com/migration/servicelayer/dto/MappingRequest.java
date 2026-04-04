package com.migration.servicelayer.dto;

public record MappingRequest(
        String payloadJsonSample,
        String lakehouseSchema
) {}