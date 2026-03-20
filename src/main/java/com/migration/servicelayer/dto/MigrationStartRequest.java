package com.migration.servicelayer.dto;

public record MigrationStartRequest(
        String originTable,
        String targetTable
) {}