package com.migration.servicelayer.dto;

public record MapeamentoRequest(
        String originSchema,
        String targetSchema
) {}