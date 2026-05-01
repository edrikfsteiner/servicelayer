package com.migration.servicelayer.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.TransformationBatchMessage;
import com.migration.servicelayer.exception.SchemaValidationException;
import com.migration.servicelayer.model.BronzeDocument;
import com.migration.servicelayer.model.SchemaMappingRules;
import com.migration.servicelayer.model.SchemaProperties;
import com.migration.servicelayer.model.SilverDocument;
import com.migration.servicelayer.model.TransformationError;
import com.migration.servicelayer.model.TransformType;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Slf4j
@Component
public class TransformationWorker {

    private static final String BRONZE = "bronze";
    private static final String SILVER = "silver";

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    @RabbitListener(queues = "${app.messaging.queue-transformation}")
    public void processTransformationBatch(TransformationBatchMessage message) {
        SchemaMappingRules schema = message.schema();
        JsonSchema jsonSchemaFields = schemaFactory.getSchema(objectMapper.valueToTree(schema.getFields()));
        List<SilverDocument> silverDocuments = new ArrayList<>();
        List<TransformationError> transformationErrors = new ArrayList<>();

        log.info(
                "Recebido lote para tenant='{}', eventType='{}' com {} registros.",
                schema.getTenantId(), schema.getEventType(), message.bronzeDocuments().size()
        );

        message.bronzeDocuments().forEach(bronzeDocument -> {
            try {
                JsonNode bronzePayload = objectMapper.valueToTree(bronzeDocument.getPayload());
                validateSchema(jsonSchemaFields, bronzePayload);
                Map<String, Object> data = applySchemaRules(bronzePayload, schema.getFields());
                silverDocuments.add(toSilver(bronzeDocument, data));
            } catch (SchemaValidationException e) {
                transformationErrors.add(TransformationError.builder()
                        .bronzeId(bronzeDocument.getId())
                        .tenantId(schema.getTenantId())
                        .eventType(schema.getEventType())
                        .processedAt(LocalDateTime.now())
                        .errors(e.getValidationErrors())
                        .build()
                );
            }
        });

        if (hasPrimaryKey(schema.getFields())) {
            saveSilverWithPrimaryKey(silverDocuments, schema);
        } else {
            mongoTemplate.insert(silverDocuments);
        }

        if (!transformationErrors.isEmpty()) {
            mongoTemplate.insert(transformationErrors);
            log.warn(
                    "Lote parcialmente validado: {} registros processados com falha de esquema e enviados para quarentena.",
                    transformationErrors.size()
            );
        }

        List<String> bronzeIds = message.bronzeDocuments().stream().map(BronzeDocument::getId).toList();
        mongoTemplate.updateMulti(
                query(where("_id").in(bronzeIds)),
                new Update().set("processed", true).set("queued", false),
                BRONZE
        );

        log.info(
                "Batch concluído: {} inseridos na '{}', {} erros.",
                silverDocuments.size(), SILVER, transformationErrors.size()
        );
    }

    private void validateSchema(JsonSchema schemaFields, JsonNode bronzePayload) {
        Set<ValidationMessage> errors = schemaFields.validate(bronzePayload);
        if (!errors.isEmpty()) {
            throw new SchemaValidationException("Payload inválido para tratamento.", errors);
        }
    }

    private SilverDocument toSilver(BronzeDocument bronzeDocument, Map<String, Object> data) {
        return SilverDocument.builder()
                .bronzeId(bronzeDocument.getId())
                .tenantId(bronzeDocument.getTenantId())
                .eventType(bronzeDocument.getEventType())
                .processedAt(LocalDateTime.now())
                .data(data)
                .build();
    }

    private Map<String, Object> applySchemaRules(JsonNode bronzePayload, Map<String, Object> fields) {
        Map<String, Object> payload = objectMapper.convertValue(bronzePayload, new TypeReference<>() {});
        if (payload == null) {
            payload = new HashMap<>();
        }

        Map<String, Object> properties = asMapStringObject(fields.get(SchemaProperties.PROPERTIES.getValue()));
        if (properties == null) {
            return payload;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String sourceField = entry.getKey();
            Map<String, Object> fieldConfig = asMapStringObject(entry.getValue());
            if (fieldConfig == null) {
                continue;
            }

            boolean hasValue = payload.containsKey(sourceField);
            boolean hasDefaultValue = fieldConfig.containsKey(SchemaProperties.DEFAULT_VALUE.getValue());
            if (!hasValue && !hasDefaultValue) {
                continue;
            }

            Object value = hasValue ? payload.get(sourceField) : fieldConfig.get(SchemaProperties.DEFAULT_VALUE.getValue());
            value = applyTransforms(value, fieldConfig.get(SchemaProperties.TRANSFORM.getValue()));

            String targetField = targetFieldName(sourceField, fieldConfig);
            if (!targetField.equals(sourceField)) {
                payload.remove(sourceField);
            }

            payload.put(targetField, value);
        }

        return payload;
    }

    private Map<String, Object> asMapStringObject(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private Object applyTransforms(Object value, Object transforms) {
        if (value == null || transforms == null) {
            return value;
        }

        if (transforms instanceof Iterable<?> iterable) {
            Object transformed = value;
            for (Object transform : iterable) {
                transformed = applyTransform(transformed, transform);
            }
            return transformed;
        }

        return applyTransform(value, transforms);
    }

    private Object applyTransform(Object value, Object transform) {
        if (value == null || transform == null) {
            return value;
        }

        TransformType transformType = TransformType.valueOf(transform.toString().trim().toLowerCase());
        return switch (transformType) {
            case UPPERCASE -> value.toString().toUpperCase();
            case LOWERCASE -> value.toString().toLowerCase();
            case TRIM -> value.toString().trim();
        };
    }

    private String targetFieldName(String sourceField, Map<String, Object> fieldConfig) {
        Object renameTo = fieldConfig.get(SchemaProperties.RENAME_TO.getValue());
        if (renameTo instanceof String targetField && !targetField.isBlank()) {
            return targetField.trim();
        }
        return sourceField;
    }

    private void saveSilverWithPrimaryKey(List<SilverDocument> silverDocuments, SchemaMappingRules schema) {
        if (silverDocuments.isEmpty()) {
            return;
        }

        Map<String, SilverDocument> documentsByHash = new LinkedHashMap<>();
        List<SilverDocument> documentsWithoutPrimaryKey = new ArrayList<>();

        for (SilverDocument silverDocument : silverDocuments) {
            Map<String, Object> primaryKeyValues = primaryKeyValues(silverDocument.getData(), schema.getFields());
            if (primaryKeyValues.isEmpty()) {
                log.warn(
                        "Registro bronzeId='{}' sem valor de primary key para tenant='{}', eventType='{}'.",
                        silverDocument.getBronzeId(), silverDocument.getTenantId(), silverDocument.getEventType()
                );
                documentsWithoutPrimaryKey.add(silverDocument);
                continue;
            }

            String primaryKeyHash = primaryKeyHash(primaryKeyValues);
            silverDocument.setPrimaryKeyHash(primaryKeyHash);
            documentsByHash.put(primaryKeyHash, silverDocument);
        }

        if (!documentsWithoutPrimaryKey.isEmpty()) {
            mongoTemplate.insert(documentsWithoutPrimaryKey, SilverDocument.class);
        }

        if (documentsByHash.isEmpty()) {
            return;
        }

        Set<String> existingHashes = existingPrimaryKeyHashes(
                schema.getTenantId(),
                schema.getEventType(),
                documentsByHash.keySet()
        );

        List<SilverDocument> newDocuments = new ArrayList<>();
        List<SilverDocument> existingDocuments = new ArrayList<>();
        documentsByHash.forEach((primaryKeyHash, silverDocument) -> {
            if (existingHashes.contains(primaryKeyHash)) {
                existingDocuments.add(silverDocument);
            } else {
                newDocuments.add(silverDocument);
            }
        });

        insertNewDocuments(newDocuments);
        updateExistingDocuments(existingDocuments);
    }

    private Set<String> existingPrimaryKeyHashes(String tenantId, String eventType, Collection<String> primaryKeyHashes) {
        Query query = query(
                where("tenantId").is(tenantId)
                .and("eventType").is(eventType)
                .and("primaryKeyHash").in(primaryKeyHashes)
        );
        query.fields().include("primaryKeyHash");

        Set<String> existingHashes = new HashSet<>();
        mongoTemplate.find(query, SilverDocument.class).forEach(document ->
                existingHashes.add(document.getPrimaryKeyHash())
        );
        return existingHashes;
    }

    private void insertNewDocuments(List<SilverDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }

        mongoTemplate.insert(documents);
    }

    private void updateExistingDocuments(List<SilverDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, SilverDocument.class);
        documents.forEach(document ->
                bulkOps.updateOne(silverQuery(document), silverUpdate(document))
        );
        bulkOps.execute();
    }

    private Query silverQuery(SilverDocument silverDocument) {
        return query(
                where("tenantId").is(silverDocument.getTenantId())
                .and("eventType").is(silverDocument.getEventType())
                .and("primaryKeyHash").is(silverDocument.getPrimaryKeyHash())
        );
    }

    private Update silverUpdate(SilverDocument silverDocument) {
        return new Update()
                .set("bronzeId", silverDocument.getBronzeId())
                .set("processedAt", silverDocument.getProcessedAt())
                .set("data", silverDocument.getData());
    }

    private String primaryKeyHash(Map<String, Object> primaryKeyValues) {
        StringBuilder source = new StringBuilder();
        primaryKeyValues.forEach((fieldName, value) -> {
            String text = String.valueOf(value);
            source.append(fieldName.length()).append(':').append(fieldName)
                    .append('=')
                    .append(text.length()).append(':').append(text)
                    .append(';');
        });

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel para primaryKeyHash", e);
        }
    }

    boolean hasPrimaryKey(Map<String, Object> fields) {
        return !primaryKeyFields(fields).isEmpty();
    }

    private List<String> primaryKeyFields(Map<String, Object> fields) {
        Map<String, Object> properties = asMapStringObject(fields.get(SchemaProperties.PROPERTIES.getValue()));
        if (properties == null) {
            return List.of();
        }

        List<String> primaryKeyFields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> fieldConfig = asMapStringObject(entry.getValue());
            if (fieldConfig != null && Boolean.TRUE.equals(fieldConfig.get(SchemaProperties.PRIMARY_KEY.getValue()))) {
                primaryKeyFields.add(targetFieldName(entry.getKey(), fieldConfig));
            }
        }

        return primaryKeyFields;
    }

    private Map<String, Object> primaryKeyValues(Map<String, Object> data, Map<String, Object> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> primaryKeyFields = primaryKeyFields(fields);

        for (String fieldName : primaryKeyFields) {
            if (!data.containsKey(fieldName) || data.get(fieldName) == null) {
                return Map.of();
            }
            values.put(fieldName, data.get(fieldName));
        }

        return values;
    }
}
