package com.migration.servicelayer.scheduler;

import com.migration.servicelayer.dto.SchemaFieldRule;
import com.migration.servicelayer.model.FieldType;
import com.migration.servicelayer.model.SchemaMappingRules;
import com.migration.servicelayer.repository.SchemaMappingRulesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Scheduled(cron = "${app.transformation.scheduler.cron}")
    public void processAllTenants() {
        Criteria unprocessed = new Criteria().andOperator(
                Criteria.where("_processed").ne(true),
                Criteria.where("eventType").ne(DEFAULT_EVENT_TYPE)
        );

        long pendingCount = mongoTemplate.count(Query.query(unprocessed), BRONZE);
        if (pendingCount == 0) {
            log.info("Sem registros pendentes na camada Bronze");
            return;
        }

        List<SchemaMappingRules> allRules = schemaMappingRulesRepository.findAll();
        if (allRules.isEmpty()) {
            log.warn("{} registros da camada Bronze pendentes, mas sem esquema registrado.", pendingCount);
            return;
        }

        flagSchemaMissingRecords();
        log.info("{} registros da bronze pendentes, {} regras encontradas.", pendingCount, allRules.size());

        for (SchemaMappingRules rules : allRules) {
            try {
                processTenant(rules);
            } catch (Exception e) {
                log.error(
                        "Falha na transformação para tenantId='{}', eventType='{}': {}",
                        rules.getTenantId(), rules.getEventType(), e.getMessage(), e
                );
            }
        }
    }

    void flagSchemaMissingRecords() {
        Criteria rawDataFilter = new Criteria().andOperator(
                Criteria.where("_processed").ne(true),
                Criteria.where("eventType").is(DEFAULT_EVENT_TYPE)
        );

        long count = mongoTemplate.count(Query.query(rawDataFilter), BRONZE);
        if (count == 0) {
            return;
        }

        log.warn("Encontrados {} registros na collection {} aguardando schema (eventType={}).", count, BRONZE, DEFAULT_EVENT_TYPE);
    }

    void processTenant(SchemaMappingRules rules) {
        String tenantId = rules.getTenantId();
        String eventType = rules.getEventType();

        Criteria filter = new Criteria().andOperator(
                Criteria.where("_processed").ne(true),
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
            executor.submit(() -> processBatch(rules, filter, skip));
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Processamento em batch de transformação interrompido para tenant='{}', eventType='{}'.", tenantId, eventType);
        }

        log.info("Processamento concluído para tenant='{}', eventType='{}'.", tenantId, eventType);
    }

    void processBatch(SchemaMappingRules rules, Criteria filter, long skip) {
        Query query = Query.query(filter).skip(skip).limit(BATCH_SIZE);
        List<Document> bronzeRecords = mongoTemplate.find(query, Document.class, BRONZE);
        if (bronzeRecords.isEmpty()) {
            return;
        }

        List<Document> silverRecords = bronzeRecords.stream()
                .map(record -> toSilver(record, rules.getFields()))
                .toList();

        mongoTemplate.insert(silverRecords, SILVER);

        List<Object> ids = bronzeRecords.stream().map(d -> d.get("_id")).toList();
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").in(ids)),
                Update.update("_processed", true),
                BRONZE
        );

        log.debug("Batch feito: {} registros para '{}'.", silverRecords.size(), SILVER);
    }

    Document toSilver(Document bronzeRecord, List<SchemaFieldRule> rules) {
        Document document = new Document();
        document.put("_bronzeId",    bronzeRecord.get("_id").toString());
        document.put("tenantId",     bronzeRecord.getString("tenantId"));
        document.put("eventType",    bronzeRecord.getString("eventType"));
        document.put("_processedAt", LocalDateTime.now());

        Object rawPayload = bronzeRecord.get("payload");
        if (!(rawPayload instanceof Document payload)) {
            return document;
        }

        for (SchemaFieldRule rule : rules) {
            try {
                document.put(rule.fieldName(), coerce(payload.get(rule.fieldName()), rule));
            } catch (Exception e) {
                log.warn(
                        "Coerção falhada: field='{}', type={}, value='{}': {}",
                        rule.fieldName(), rule.fieldType(), payload.get(rule.fieldName()), e.getMessage()
                );
                document.put(rule.fieldName(), rule.nullable() ? null : defaultValue(rule.fieldType()));
            }
        }

        return document;
    }

    Object coerce(Object raw, SchemaFieldRule rule) {
        if (raw == null) {
            return rule.nullable() ? null : defaultValue(rule.fieldType());
        }

        String value = raw.toString();
        if (rule.trim()) {
            value = value.trim();
        }

        return switch (rule.fieldType()) {
            case STRING -> value;
            case INTEGER -> Integer.parseInt(value);
            case DOUBLE -> Double.parseDouble(value);
            case DATE -> LocalDate.parse(value).toString();
            case BOOLEAN -> Boolean.parseBoolean(value);
        };
    }

    // TODO: deve-se criar o campo Object defaultValue no SchemaFieldRule e usá-lo para setar o valor do campo
    private Object defaultValue(FieldType fieldType) {
        return switch (fieldType) {
            case INTEGER -> 0;
            case DOUBLE -> 0.0;
            case BOOLEAN -> false;
            default -> "";
        };
    }
}