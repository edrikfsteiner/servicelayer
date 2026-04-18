package com.migration.servicelayer.model;

import com.migration.servicelayer.dto.SilverFieldRule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB document that persists the AI-inferred field transformation rules
 * for a given tenant and event type.
 *
 * Collection: {@code silver_rules}
 * Unique index on (tenantId, eventType) prevents duplicate rule sets.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "silver_rules")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_event_unique",
                       def = "{'tenantId': 1, 'eventType': 1}",
                       unique = true)
})
public class SilverMappingRules {

    @Id
    private String id;

    private String tenantId;
    private String eventType;

    /** Field-level transformation rules produced by the AI Profiler. */
    private List<SilverFieldRule> fields;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
