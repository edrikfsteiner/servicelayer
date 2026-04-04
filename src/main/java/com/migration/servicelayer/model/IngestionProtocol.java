package com.migration.servicelayer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ingestion_protocols")
public class IngestionProtocol {
    @Id
    private ObjectId id;

    private String tenantId;
    private String eventType;
    private ProtocolStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
