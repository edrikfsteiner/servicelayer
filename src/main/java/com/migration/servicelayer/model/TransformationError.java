package com.migration.servicelayer.model;

import com.networknt.schema.ValidationMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transformation_errors")
public class TransformationError {
    @Id
    private String id;

    private String bronzeId;
    private String tenantId;
    private String eventType;
    private LocalDateTime processedAt;
    private Set<ValidationMessage> errors;
}
