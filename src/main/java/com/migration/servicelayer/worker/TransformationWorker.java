package com.migration.servicelayer.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.dto.TransformationBatchMessage;
import com.migration.servicelayer.model.BronzeDocument;
import com.migration.servicelayer.model.SchemaMappingRules;
import com.migration.servicelayer.model.SilverDocument;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
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
    private static final String PROPERTIES = "properties";
    private static final String DEFAULT_VALUE = "default-value";
    private static final String RENAME_TO = "x-rename-to";
    private static final String TRANSFORM = "x-transform";
    private static final String PRIMARY_KEY = "x-primary-key";

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    @RabbitListener(queues = "${app.messaging.queue-transformation}")
    public void processTransformationBatch(TransformationBatchMessage message) {
        log.info(
                "Recebido lote para tenant='{}', eventType='{}' com {} registros.",
                message.tenantId(), message.eventType(), message.bronzeDocuments().size()
        );

        Query schemaQuery = query(
                where("tenantId").is(message.tenantId())
                .and("eventType").is(message.eventType())
        );

        //essa validação  de se tem ou nao schema podia ta na run da silver, pq ele ta enfilerando e fica estourando erro.
        SchemaMappingRules schema = mongoTemplate.findOne(schemaQuery, SchemaMappingRules.class);
        if (schema == null) {
            log.error("Schema não encontrado para tenant='{}' e eventType='{}'. Abortando batch.", message.tenantId(), message.eventType());
            return;
        }

        List<SilverDocument> silverDocuments = message.bronzeDocuments().stream()
                .map(document -> toSilver(document, schema))
                .toList();

        if (hasPrimaryKey(schema.getFields())) {
            saveSilverWithPrimaryKey(silverDocuments, schema);
        } else {
            mongoTemplate.insert(silverDocuments);
        }

        List<String> bronzeIds = message.bronzeDocuments().stream().map(BronzeDocument::getId).toList();
        mongoTemplate.updateMulti(
                query(where("_id").in(bronzeIds)),
                new Update().set("processed", true).set("queued", false),
                BRONZE
        );

        log.debug("Batch concluído com sucesso: {} registros inseridos em '{}'.", silverDocuments.size(), SILVER);
    }

    // acho que da de criar uma classe separada pra fazer essa transformação, pq ta ficando grande e tem bastante regra de negócio.
    private SilverDocument toSilver(BronzeDocument bronzeDocument, SchemaMappingRules schema) {
        JsonNode bronzePayload = objectMapper.valueToTree(bronzeDocument.getPayload());
        JsonSchema fields = schemaFactory.getSchema(objectMapper.valueToTree(schema.getFields()));

        //não sei se isso ta funcionado, porque deu uns erro de squema e não tem nada na fila do dql.
        Set<ValidationMessage> errors = fields.validate(bronzePayload);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.format(
                    "Payload inválido para tratamento. bronzeId = %s, errors: %s", bronzeDocument.getId(), errors
            ));
        }

        Map<String, Object> data = applySchemaRules(bronzePayload, schema.getFields());

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

        Map<String, Object> properties = asMapStringObject(fields.get(PROPERTIES));
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
            boolean hasDefaultValue = fieldConfig.containsKey(DEFAULT_VALUE);
            if (!hasValue && !hasDefaultValue) {
                continue;
            }

            Object value = hasValue ? payload.get(sourceField) : fieldConfig.get(DEFAULT_VALUE);
            value = applyTransforms(value, fieldConfig.get(TRANSFORM));

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

        String transformName = transform.toString().trim().toLowerCase();
        return switch (transformName) {
            case "uppercase" -> value.toString().toUpperCase();
            case "lowercase" -> value.toString().toLowerCase();
            case "trim" -> value.toString().trim();
            default -> value;
        };
    }

    private String targetFieldName(String sourceField, Map<String, Object> fieldConfig) {
        Object renameTo = fieldConfig.get(RENAME_TO);
        if (renameTo instanceof String targetField && !targetField.isBlank()) {
            return targetField.trim();
        }
        return sourceField;
    }

    private void saveSilverWithPrimaryKey(List<SilverDocument> silverDocuments, SchemaMappingRules schema) {
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

        SilverDocument firstDocument = documentsByHash.values().iterator().next();
        Set<String> existingHashes = existingPrimaryKeyHashes(
                firstDocument.getTenantId(),
                firstDocument.getEventType(),
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

        insertNewSilverDocuments(newDocuments);
        updateExistingSilverDocuments(existingDocuments);
    }

    private Set<String> existingPrimaryKeyHashes(String tenantId, String eventType, Collection<String> primaryKeyHashes) {
        Query query = query(
                where("tenantId").is(tenantId)
                .and("eventType").is(eventType)
                .and("primaryKeyHash").in(primaryKeyHashes)
        );
        query.fields().include("primaryKeyHash");

        Set<String> existingHashes = new HashSet<>();
        mongoTemplate.find(query, SilverDocument.class)
                .forEach(document -> existingHashes.add(document.getPrimaryKeyHash()));
        return existingHashes;
    }

    private void insertNewSilverDocuments(List<SilverDocument> silverDocuments) {
        if (silverDocuments.isEmpty()) {
            return;
        }

        try {
            mongoTemplate.insert(silverDocuments, SilverDocument.class);
        } catch (DuplicateKeyException e) {
            log.warn("Duplicidade detectada ao inserir lote silver. Reexecutando {} registros como upsert.", silverDocuments.size());
            upsertSilverDocuments(silverDocuments);
        }
    }

    private void updateExistingSilverDocuments(List<SilverDocument> silverDocuments) {
        if (silverDocuments.isEmpty()) {
            return;
        }

        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, SilverDocument.class);
        silverDocuments.forEach(silverDocument ->
                bulkOperations.updateOne(silverQuery(silverDocument), silverUpdate(silverDocument))
        );
        bulkOperations.execute();
    }

    private void upsertSilverDocuments(List<SilverDocument> silverDocuments) {
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, SilverDocument.class);
        silverDocuments.forEach(silverDocument ->
                bulkOperations.upsert(silverQuery(silverDocument), silverUpdate(silverDocument))
        );
        bulkOperations.execute();
    }

    private Query silverQuery(SilverDocument silverDocument) {
        Criteria criteria = where("tenantId").is(silverDocument.getTenantId())
                .and("eventType").is(silverDocument.getEventType())
                .and("primaryKeyHash").is(silverDocument.getPrimaryKeyHash());
        return query(criteria);
    }

    private Update silverUpdate(SilverDocument silverDocument) {
        return new Update()
                .set("bronzeId", silverDocument.getBronzeId())
                .set("tenantId", silverDocument.getTenantId())
                .set("eventType", silverDocument.getEventType())
                .set("primaryKeyHash", silverDocument.getPrimaryKeyHash())
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
        Map<String, Object> properties = asMapStringObject(fields.get(PROPERTIES));
        if (properties == null) {
            return List.of();
        }

        List<String> primaryKeyFields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> fieldConfig = asMapStringObject(entry.getValue());
            if (fieldConfig != null && Boolean.TRUE.equals(fieldConfig.get(PRIMARY_KEY))) {
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
