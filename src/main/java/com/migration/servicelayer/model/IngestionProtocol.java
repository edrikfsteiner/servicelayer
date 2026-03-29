package com.migration.servicelayer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngestionProtocol {
    private String id;
    private String tenantId;
    private String eventType;
    private ProtocolStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
