package com.migration.servicelayer.dto;

public record MapeamentoRequest(
        String schemaOrigem,
        String schemaDestino
) {}