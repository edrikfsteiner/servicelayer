package com.migration.servicelayer.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Document(collection = "ingestion_protocols")
public class IngestionProtocol {
    @Id
    private String id;

    private String tenantId;
    private String eventType;
    private ProtocolStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
