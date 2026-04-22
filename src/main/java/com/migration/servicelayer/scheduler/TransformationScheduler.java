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
import java.util.HashMap;
import java.util.List;
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

        for (SchemaMappingRules schema : allSchemas) {
            try {
                processTenant(schema);
            } catch (Exception e) {
                log.error(
                        "Falha na transformação para tenantId='{}', eventType='{}': {}",
                        schema.getTenantId(), schema.getEventType(), e.getMessage(), e
                );
            }
        }
    }

    private void processTenant(SchemaMappingRules schema) {
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
    }

    private void processBatch(SchemaMappingRules schema, Query query) {
        List<BronzeDocument> bronzeDocuments = mongoTemplate.find(query, BronzeDocument.class);
        if (bronzeDocuments.isEmpty()) {
            return;
        }

        List<SilverDocument> silverDocuments = bronzeDocuments.stream()
                .map(document -> toSilver(document, schema))
                .toList();

        mongoTemplate.insert(silverDocuments);

        List<String> bronzeIds = bronzeDocuments.stream().map(BronzeDocument::getId).toList();
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").in(bronzeIds)),
                Update.update("processed", true),
                BRONZE
        );

        log.debug("Batch feito: {} registros para '{}'.", silverDocuments.size(), SILVER);
    }

    private SilverDocument toSilver(BronzeDocument bronzeDocument, SchemaMappingRules schema) {
        JsonNode bronzePayload = objectMapper.valueToTree(bronzeDocument.getPayload());
        JsonSchema fields = schemaFactory.getSchema(objectMapper.valueToTree(schema.getFields()));

        Set<ValidationMessage> errors = fields.validate(bronzePayload);
        if (!errors.isEmpty()) {
            log.error("Payload inválido para tratamento. bronzeId = {}, errors: {}", bronzeDocument.getId(), errors);
        }

        Map<String, Object> data = applySchemaDefaults(bronzePayload, schema.getFields());

        return SilverDocument.builder()
                .bronzeId(bronzeDocument.getId())
                .tenantId(bronzeDocument.getTenantId())
                .eventType(bronzeDocument.getEventType())
                .processedAt(LocalDateTime.now())
                .data(data)
                .build();
    }

    private Map<String, Object> applySchemaDefaults(JsonNode bronzePayload, Map<String, Object> fields) {
        Map<String, Object> result = objectMapper.convertValue(bronzePayload, new TypeReference<>() {});
        if (result == null) {
            result = new HashMap<>();
        }

        Map<String, Object> properties = (Map<String, Object>) fields.get("properties");
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String fieldName = entry.getKey();
                Map<String, Object> fieldConfig = (Map<String, Object>) entry.getValue();

                if (!result.containsKey(fieldName) && fieldConfig.containsKey("default")) {
                    result.put(fieldName, fieldConfig.get("default"));
                }
            }
        }

        return result;
    }
}