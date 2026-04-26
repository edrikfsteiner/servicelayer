package com.migration.servicelayer.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String expiresIn
) {}
