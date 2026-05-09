package com.migration.servicelayer.scheduler;

import com.migration.servicelayer.dto.TransformationBatchMessage;
import com.migration.servicelayer.model.BronzeDocument;
import com.migration.servicelayer.model.SchemaMappingRules;
import com.migration.servicelayer.repository.SchemaMappingRulesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

@RequiredArgsConstructor
@Slf4j
@Component
public class TransformationScheduler {

    private static final int BATCH_SIZE = 1000;
    private static final int MAX_IN_FLIGHT_BATCHES = 10;
    private static final int MAX_IN_FLIGHT_DOCUMENTS = BATCH_SIZE * MAX_IN_FLIGHT_BATCHES;
    private static final long REFILL_DELAY_MILLIS = 1_000L;
    private static final String BRONZE = "bronze";
    private static final String DEFAULT_EVENT_TYPE = "raw_data";

    private final MongoTemplate mongoTemplate;
    private final SchemaMappingRulesRepository schemaMappingRulesRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routing-key-transformation}")
    private String transformationRoutingKey;

    @Scheduled(cron = "${app.transformation.scheduler.cron}")
    public void processAllTenants() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Transformacao ja esta em execucao. Nova chamada ignorada.");
            return;
        }

        try {
            processWithBackpressure();
        } finally {
            running.set(false);
        }
    }

    private void processWithBackpressure() {
        List<SchemaMappingRules> allSchemas = schemaMappingRulesRepository.findAll();

        if (allSchemas.isEmpty()) {
            log.warn("Existem registros da camada Bronze pendentes, mas sem esquema registrado.");
            return;
        }

        log.info(
                "Transformacao iniciada com limite de {} batches em voo ({} registros).",
                MAX_IN_FLIGHT_BATCHES, MAX_IN_FLIGHT_DOCUMENTS
        );

        while (true) {
            long pendingCount = countPendingDocuments(allSchemas);
            if (pendingCount == 0) {
                log.info("Sem registros pendentes na camada Bronze");
                return;
            }

            long inFlightDocuments = countInFlightDocuments(allSchemas);
            int availableDocuments = (int) Math.max(0, MAX_IN_FLIGHT_DOCUMENTS - inFlightDocuments);

            if (availableDocuments == 0) {
                log.info(
                        "Limite de batches em voo atingido: {} registros queued=true. Aguardando workers liberarem espaco.",
                        inFlightDocuments
                );
                if (!waitBeforeNextPass()) {
                    return;
                }
                continue;
            }

            int sentDocuments = 0;
            for (SchemaMappingRules schema : allSchemas) {
                if (DEFAULT_EVENT_TYPE.equals(schema.getEventType())) {
                    continue;
                }

                if (availableDocuments <= 0) {
                    break;
                }

                int sent = queueTenantBatches(schema, availableDocuments);
                sentDocuments += sent;
                availableDocuments -= sent;
            }

            if (sentDocuments == 0) {
                log.warn("{} registros pendentes, mas nenhum batch foi enfileirado.", pendingCount);
                return;
            }

            if (!waitBeforeNextPass()) {
                return;
            }
        }
    }

    private int queueTenantBatches(SchemaMappingRules schema, int maxDocumentsToQueue) {
        if (DEFAULT_EVENT_TYPE.equals(schema.getEventType())) {
            return 0;
        }

        Criteria filter = new Criteria().andOperator(
                where("processed").is(false),
                where("queued").is(false),
                where("tenantId").is(schema.getTenantId()),
                where("eventType").is(schema.getEventType())
        );

        long total = mongoTemplate.count(query(filter), BRONZE);
        if (total == 0) {
            return 0;
        }

        int documentsToQueue = (int) Math.min(total, maxDocumentsToQueue);
        int totalBatches = (int) Math.ceil((double) documentsToQueue / BATCH_SIZE);
        log.info(
                "Enfileirando tenant='{}', eventType='{}': {} de {} pendentes em {} batches.",
                schema.getTenantId(), schema.getEventType(), documentsToQueue, total, totalBatches
        );

        return sendBatches(totalBatches, documentsToQueue, filter, schema);
    }

    private int sendBatches(int batches, int maxDocumentsToQueue, Criteria filter, SchemaMappingRules schema) {
        int sentDocuments = 0;
        int remainingDocuments = maxDocumentsToQueue;

        for (int page = 0; page < batches; page++) {
            Query query = query(filter).limit(Math.min(BATCH_SIZE, remainingDocuments));

            List<BronzeDocument> batch = mongoTemplate.find(query, BronzeDocument.class);
            if (batch.isEmpty()) {
                break;
            }

            List<String> ids = batch.stream().map(BronzeDocument::getId).toList();
            mongoTemplate.updateMulti(
                    query(where("_id").in(ids)),
                    update("queued", true),
                    BRONZE
            );

            TransformationBatchMessage message = new TransformationBatchMessage(schema, batch);
            rabbitTemplate.convertAndSend(exchange, transformationRoutingKey, message);
            log.info("Enviado batch com {} registros", ids.size());

            sentDocuments += ids.size();
            remainingDocuments -= ids.size();
        }

        return sentDocuments;
    }

    private long countPendingDocuments(List<SchemaMappingRules> schemas) {
        return schemas.stream()
                .filter(schema -> !DEFAULT_EVENT_TYPE.equals(schema.getEventType()))
                .mapToLong(schema -> mongoTemplate.count(query(pendingCriteria(schema)), BRONZE))
                .sum();
    }

    private long countInFlightDocuments(List<SchemaMappingRules> schemas) {
        return schemas.stream()
                .filter(schema -> !DEFAULT_EVENT_TYPE.equals(schema.getEventType()))
                .mapToLong(schema -> mongoTemplate.count(query(inFlightCriteria(schema)), BRONZE))
                .sum();
    }

    private Criteria pendingCriteria(SchemaMappingRules schema) {
        return new Criteria().andOperator(
                where("processed").is(false),
                where("queued").is(false),
                where("tenantId").is(schema.getTenantId()),
                where("eventType").is(schema.getEventType())
        );
    }

    private Criteria inFlightCriteria(SchemaMappingRules schema) {
        return new Criteria().andOperator(
                where("processed").is(false),
                where("queued").is(true),
                where("tenantId").is(schema.getTenantId()),
                where("eventType").is(schema.getEventType())
        );
    }

    private boolean waitBeforeNextPass() {
        try {
            Thread.sleep(REFILL_DELAY_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Transformacao interrompida durante espera entre batches.");
            return false;
        }
    }
}
