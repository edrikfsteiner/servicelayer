package com.migration.servicelayer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MigrationProtocol {
    private String id;
    private String originTable;
    private String targetTable;
    private long totalRecords;
    private long processedRecords;
    private long failedRecords;
    private ProtocolStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
