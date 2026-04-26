package com.migration.servicelayer.dto;

public record AuthRegisterRequest(String clientId, String clientSecret, String tenantId) {}
