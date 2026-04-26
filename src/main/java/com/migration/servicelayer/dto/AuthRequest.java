package com.migration.servicelayer.dto;

public record AuthRequest(
        String clientId,
        String clientSecret
) {}