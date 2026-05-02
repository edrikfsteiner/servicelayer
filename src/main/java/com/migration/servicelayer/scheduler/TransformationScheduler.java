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

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

@RequiredArgsConstructor
@Slf4j
@Component
public class TransformationScheduler {

    private static final int BATCH_SIZE = 500;
    private static final String BRONZE = "bronze";
    private static final String DEFAULT_EVENT_TYPE = "raw_data";

    private final MongoTemplate mongoTemplate;
    private final SchemaMappingRulesRepository schemaMappingRulesRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routing-key-transformation}")
    private String transformationRoutingKey;

    @Scheduled(cron = "${app.transformation.scheduler.cron}")
    public void processAllTenants() {
        Criteria unprocessed = new Criteria().andOperator(
                where("processed").is(false),
                where("queued").is(false),
                where("eventType").ne(DEFAULT_EVENT_TYPE)
        );

        long pendingCount = mongoTemplate.count(query(unprocessed), BRONZE);
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
        allSchemas.forEach(this::queueTenantBatches);
    }

    private void queueTenantBatches(SchemaMappingRules schema) {
        Criteria filter = new Criteria().andOperator(
                where("processed").is(false),
                where("queued").is(false),
                where("tenantId").is(schema.getTenantId()),
                where("eventType").is(schema.getEventType())
        );

        long total = mongoTemplate.count(query(filter), BRONZE);
        if (total == 0) {
            return;
        }

        int totalBatches = (int) Math.ceil((double) total / BATCH_SIZE);
        log.info(
                "Enfileirando tenant='{}', eventType='{}': {} registros em {} batches.",
                schema.getTenantId(), schema.getEventType(), total, totalBatches
        );

        sendBatches(totalBatches, filter, schema);
    }

    private void sendBatches(int batches, Criteria filter, SchemaMappingRules schema) {
        for (int page = 0; page < batches; page++) {
            Query query = query(filter).limit(BATCH_SIZE);

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
        }
    }
}
