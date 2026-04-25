package com.migration.servicelayer.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.model.BronzeDocument;
import com.migration.servicelayer.model.SchemaMappingRules;
import com.migration.servicelayer.model.SilverDocument;
import com.migration.servicelayer.repository.SchemaMappingRulesRepository;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
@Component
public class TransformationScheduler {

    private static final int BATCH_SIZE = 10000;
    private static final String BRONZE = "bronze";
    private static final String SILVER = "silver";
    private static final String DEFAULT_EVENT_TYPE = "raw_data";
    private static final String PROPERTIES = "properties";
    private static final String DEFAULT = "default";
    private static final String RENAME_TO = "x-rename-to";
    private static final String TRANSFORM = "x-transform";
    private static final String PRIMARY_KEY = "x-primary-key";

    private final MongoTemplate mongoTemplate;
    private final SchemaMappingRulesRepository schemaMappingRulesRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    @Scheduled(cron = "${app.transformation.scheduler.cron}")
    public void processAllTenants() {
        Criteria unprocessed = new Criteria().andOperator(
                Criteria.where("processed").is(false),
                Criteria.where("eventType").ne(DEFAULT_EVENT_TYPE)
        );

        long pendingCount = mongoTemplate.count(Query.query(unprocessed), BRONZE);
        if (pendingCount == 0) {
            log.info("Sem registros pendentes na camada Bronze");
            return;
        }

        List<SchemaMappingRules> allSchemas = schemaMappingRulesRepository.findAll();
        if (allSchemas.isEmpty()) {
            log.warn("{} registros da camada Bronze pendentes, mas sem esquema registrado.", pendingCount);
            return;
        }

        log.info("{} registros da bronze pendentes, {} regras encontradas.", pendingCount, allSchemas.size());
        allSchemas.forEach(this::processTenant);
    }

    private void processTenant(SchemaMappingRules schema) {
        try {
            String tenantId = schema.getTenantId();
            String eventType = schema.getEventType();

            Criteria filter = new Criteria().andOperator(
                    Criteria.where("processed").is(false),
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("eventType").is(eventType)
            );

            long total = mongoTemplate.count(Query.query(filter), BRONZE);
            if (total == 0) {
                log.debug("Sem registros pendentes para tenant='{}', eventType='{}'.", tenantId, eventType);
                return;
            }

            int totalBatches = (int) Math.ceil((double) total / BATCH_SIZE);
            log.info(
                    "Processando tenant='{}', eventType='{}': {} registros, {} batches.",
                    tenantId, eventType, total, totalBatches
            );

            // TODO: revisar essa parte, parece meio relaxada
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            for (int page = 0; page < totalBatches; page++) {
                final long skip = (long) page * BATCH_SIZE;
                Query query = Query.query(filter).skip(skip).limit(BATCH_SIZE);
                executor.submit(() -> processBatch(schema, query));
            }
            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Processamento em batch de transformação interrompido para tenant='{}', eventType='{}'.", tenantId, eventType);
            }

            log.info("Processamento concluído para tenant='{}', eventType='{}'.", tenantId, eventType);
        } catch (Exception e) {
            log.error(
                    "Falha na transformação para tenantId='{}', eventType='{}': {}",
                    schema.getTenantId(), schema.getEventType(), e.getMessage(), e
            );
        }
    }

    private void processBatch(SchemaMappingRules schema, Query query) {
        List<BronzeDocument> bronzeDocuments = mongoTemplate.find(query, BronzeDocument.class);
        if (bronzeDocuments.isEmpty()) {
            return;
        }

        List<SilverDocument> silverDocuments = bronzeDocuments.stream()
                .map(document -> toSilver(document, schema))
                .toList();

        if (hasPrimaryKey(schema.getFields())) {
            silverDocuments.forEach(silverDocument -> upsertSilver(silverDocument, schema));
        } else {
            mongoTemplate.insert(silverDocuments);
        }

        List<String> bronzeIds = bronzeDocuments.stream().map(BronzeDocument::getId).toList();
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").in(bronzeIds)),
                Update.update("processed", true),
                BRONZE
        );

        log.debug("Batch feito: {} registros para '{}'.", silverDocuments.size(), SILVER);
    }

    private void upsertSilver(SilverDocument silverDocument, SchemaMappingRules schema) {
        Map<String, Object> primaryKeyValues = primaryKeyValues(silverDocument.getData(), schema.getFields());
        if (primaryKeyValues.isEmpty()) {
            log.warn(
                    "Registro bronzeId='{}' sem valor de primary key para tenant='{}', eventType='{}'. Inserindo sem upsert.",
                    silverDocument.getBronzeId(), silverDocument.getTenantId(), silverDocument.getEventType()
            );
            mongoTemplate.insert(silverDocument);
            return;
        }

        Criteria criteria = Criteria.where("tenantId").is(silverDocument.getTenantId())
                .and("eventType").is(silverDocument.getEventType());
        primaryKeyValues.forEach((fieldName, value) -> criteria.and("data." + fieldName).is(value));

        Update update = new Update()
                .set("bronzeId", silverDocument.getBronzeId())
                .set("tenantId", silverDocument.getTenantId())
                .set("eventType", silverDocument.getEventType())
                .set("processedAt", silverDocument.getProcessedAt())
                .set("data", silverDocument.getData());

        mongoTemplate.upsert(Query.query(criteria), update, SilverDocument.class);
    }

    private SilverDocument toSilver(BronzeDocument bronzeDocument, SchemaMappingRules schema) {
        JsonNode bronzePayload = objectMapper.valueToTree(bronzeDocument.getPayload());
        JsonSchema fields = schemaFactory.getSchema(objectMapper.valueToTree(schema.getFields()));

        Set<ValidationMessage> errors = fields.validate(bronzePayload);
        if (!errors.isEmpty()) {
            log.error("Payload inválido para tratamento. bronzeId = {}, errors: {}", bronzeDocument.getId(), errors);
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

    Map<String, Object> applySchemaRules(JsonNode bronzePayload, Map<String, Object> fields) {
        Map<String, Object> result = objectMapper.convertValue(bronzePayload, new TypeReference<>() {});
        if (result == null) {
            result = new HashMap<>();
        }

        Map<String, Object> properties = asStringObjectMap(fields.get(PROPERTIES));
        if (properties == null) {
            return result;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String sourceField = entry.getKey();
            Map<String, Object> fieldConfig = asStringObjectMap(entry.getValue());
            if (fieldConfig == null) {
                continue;
            }

            boolean hasValue = result.containsKey(sourceField);
            boolean hasDefault = fieldConfig.containsKey(DEFAULT);
            if (!hasValue && !hasDefault) {
                continue;
            }

            Object value = hasValue ? result.get(sourceField) : fieldConfig.get(DEFAULT);
            value = applyTransforms(value, fieldConfig.get(TRANSFORM));

            String targetField = targetFieldName(sourceField, fieldConfig);
            if (!targetField.equals(sourceField)) {
                result.remove(sourceField);
            }

            result.put(targetField, value);
        }

        return result;
    }

    boolean hasPrimaryKey(Map<String, Object> fields) {
        return !primaryKeyFields(fields).isEmpty();
    }

    Map<String, Object> primaryKeyValues(Map<String, Object> data, Map<String, Object> fields) {
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

    private List<String> primaryKeyFields(Map<String, Object> fields) {
        Map<String, Object> properties = asStringObjectMap(fields.get(PROPERTIES));
        if (properties == null) {
            return List.of();
        }

        List<String> primaryKeyFields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> fieldConfig = asStringObjectMap(entry.getValue());
            if (fieldConfig != null && Boolean.TRUE.equals(fieldConfig.get(PRIMARY_KEY))) {
                primaryKeyFields.add(targetFieldName(entry.getKey(), fieldConfig));
            }
        }
        return primaryKeyFields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringObjectMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private String targetFieldName(String sourceField, Map<String, Object> fieldConfig) {
        Object renameTo = fieldConfig.get(RENAME_TO);
        if (renameTo instanceof String targetField && !targetField.isBlank()) {
            return targetField.trim();
        }
        return sourceField;
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

        String transformName = transform.toString().trim().toLowerCase(Locale.ROOT);
        return switch (transformName) {
            case "uppercase" -> value.toString().toUpperCase(Locale.ROOT);
            case "lowercase" -> value.toString().toLowerCase(Locale.ROOT);
            case "trim" -> value.toString().trim();
            default -> value;
        };
    }
}
