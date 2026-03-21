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
