package com.migration.servicelayer.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bronze_raw_data")
public class BronzeRawData {
    @Id
    private String id;

    private String protocolId;
    private String tenantId;
    private String eventType;
    private JsonNode payload;
    private LocalDateTime createdAt;
}
