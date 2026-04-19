package com.migration.servicelayer.model;

import com.migration.servicelayer.dto.SchemaFieldRule;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@Document(collection = "schema_mapping_rules")
@CompoundIndexes({
        @CompoundIndex(
                name = "tenant_event_unique",
                def = "{'tenantId': 1, 'eventType': 1}",
                unique = true
        )
})
public class SchemaMappingRules {
    @Id
    private String id;

    private String tenantId;
    private String eventType;
    private List<SchemaFieldRule> fields;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
