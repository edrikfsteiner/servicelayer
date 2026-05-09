package com.migration.servicelayer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ingestion_errors")
public class IngestionError {
    @Id
    private String id;

    private String protocolId;
    private String tenantId;
    private String eventType;
    private LocalDateTime createdAt;
    private String errorMessage;
    private Map<String, Object> payload;
}
