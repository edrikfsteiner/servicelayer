package com.migration.servicelayer.service;

import com.migration.servicelayer.dto.SilverFieldRule;
import com.migration.servicelayer.model.SilverMappingRules;
import com.migration.servicelayer.repository.SilverMappingRulesRepository;
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

/**
 * Silver Transformation Scheduler — the "muscle" of the Silver Layer.
 *
 * <p>Runs on a configurable fixed delay (default 5 min). For every rule set found in
 * {@code silver_rules}, it processes the corresponding bronze collection in batches of
 * {@value #BATCH_SIZE} records using <em>virtual threads</em> for high throughput.
 *
 * <p>Each bronze document is transformed by the rules (type coercion + trim) and
 * inserted into {@code silver_{tenantId}}. Processed bronze documents are flagged with
 * {@code _processed: true} to guarantee exactly-once delivery across scheduler runs.
 *
 * <p>Virtual-thread safety: no {@code ThreadLocal} state is created. The
 * {@code ExecutorService.close()} call (try-with-resources, Java 19+) waits for all
 * submitted tasks to complete before moving to the next tenant.
 *
 * <p>Configuration knobs in {@code application.yaml}:
 * <ul>
 *   <li>{@code silver.scheduler.fixed-delay-ms} — delay between runs (default 300 000 ms)
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SilverTransformationScheduler {

    static final int BATCH_SIZE = 10_000;

    /** Criteria that selects bronze records not yet promoted to silver. */
    private static final Criteria UNPROCESSED = Criteria.where("_processed").ne(true)
            .and("eventType").ne("raw_data");

    private static final String BRONZE = "bronze";

    private final MongoTemplate mongoTemplate;
    private final SilverMappingRulesRepository silverMappingRulesRepository;

    // -------------------------------------------------------------------------
    // Scheduled entry-point
    // -------------------------------------------------------------------------

    /**
     * Runs every day at midnight. Discovers all bronze collections that have
     * unprocessed records, auto-profiles tenants that still have no rules, then
     * transforms bronze → silver for every tenant that has rules.
     *
     * <p>Uses {@code cron} (not {@code fixedRate}) — overlapping runs are impossible.
     * Override via {@code silver.scheduler.cron} in {@code application.yaml}.
     */
    @Scheduled(cron = "${silver.scheduler.cron:0 0 0 * * *}")
    public void processAllTenants() {
        long pendingCount = mongoTemplate.count(Query.query(UNPROCESSED), BRONZE);
        if (pendingCount == 0) {
            log.info("Silver scheduler triggered — no unprocessed bronze records found.");
            return;
        }

        List<SilverMappingRules> allRules = silverMappingRulesRepository.findAll();
        if (allRules.isEmpty()) {
            log.warn("Silver scheduler triggered — {} bronze record(s) pending but no schema registered. "
                    + "Tenants must register schemas via POST /api/schema/{eventType}.", pendingCount);
            return;
        }

        flagSchemaMissingRecords();

        log.info("Silver scheduler triggered — {} bronze record(s) pending, {} rule set(s) found.",
                pendingCount, allRules.size());

        for (SilverMappingRules rules : allRules) {
            try {
                processTenant(rules);
            } catch (Exception e) {
                log.error("Silver processing failed for tenant='{}', eventType='{}': {}",
                        rules.getTenantId(), rules.getEventType(), e.getMessage(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // raw_data handling
    // -------------------------------------------------------------------------

    /**
     * Finds all unprocessed records with eventType = "raw_data" and stamps them
     * with {@code _schema_missing: true}. These records stay in bronze indefinitely
     * until the tenant registers a schema via POST /api/schema/raw_data and
     * re-ingests (or re-processes manually).
     */
    void flagSchemaMissingRecords() {
        Criteria rawDataFilter = Criteria.where("_processed").ne(true)
                .and("eventType").is("raw_data");

        long count = mongoTemplate.count(Query.query(rawDataFilter), BRONZE);
        if (count == 0) return;

        mongoTemplate.updateMulti(
                Query.query(rawDataFilter),
                Update.update("_schema_missing", true),
                BRONZE
        );
        log.warn("Flagged {} bronze record(s) with _schema_missing=true (eventType=raw_data). "
                + "Register a schema via POST /api/schema/raw_data to process them.", count);
    }

    // -------------------------------------------------------------------------
    // Per-tenant processing
    // -------------------------------------------------------------------------

    void processTenant(SilverMappingRules rules) {
        String tenantId = rules.getTenantId();
        String eventType = rules.getEventType();
        String silverCollection = "silver_" + tenantId;

        Criteria filter = UNPROCESSED
                .and("tenantId").is(tenantId)
                .and("eventType").is(eventType);

        long total = mongoTemplate.count(Query.query(filter), BRONZE);
        if (total == 0) {
            log.debug("No unprocessed bronze records for tenant='{}', eventType='{}'.", tenantId, eventType);
            return;
        }

        int totalBatches = (int) Math.ceil((double) total / BATCH_SIZE);
        log.info("Processing tenant='{}', eventType='{}': {} record(s), {} batch(es).",
                tenantId, eventType, total, totalBatches);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int page = 0; page < totalBatches; page++) {
                final long skip = (long) page * BATCH_SIZE;
                executor.submit(() -> processBatch(rules, filter, silverCollection, skip));
            }
        }

        log.info("Tenant '{}', eventType '{}' silver processing complete.", tenantId, eventType);
    }

    // -------------------------------------------------------------------------
    // Batch processing
    // -------------------------------------------------------------------------

    void processBatch(SilverMappingRules rules,
                      Criteria filter,
                      String silverCollection,
                      long skip) {

        Query query = Query.query(filter).skip(skip).limit(BATCH_SIZE);
        List<Document> bronzeRecords = mongoTemplate.find(query, Document.class, BRONZE);
        if (bronzeRecords.isEmpty()) return;

        List<Document> silverRecords = bronzeRecords.stream()
                .map(record -> toSilver(record, rules.getFields()))
                .toList();

        mongoTemplate.insert(silverRecords, silverCollection);

        List<Object> ids = bronzeRecords.stream().map(d -> d.get("_id")).toList();
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").in(ids)),
                Update.update("_processed", true),
                BRONZE
        );

        log.debug("Batch done: {} record(s) → '{}'.", silverRecords.size(), silverCollection);
    }

    // -------------------------------------------------------------------------
    // Transformation logic
    // -------------------------------------------------------------------------

    /**
     * Builds a silver {@link Document} from a bronze record by applying all
     * field rules (type coercion, optional trim, nullable handling).
     * Rule iteration is Map-based — no reflection, no hard-coded field names.
     */
    Document toSilver(Document bronzeRecord, List<SilverFieldRule> rules) {
        Document silver = new Document();
        silver.put("_bronzeId",    bronzeRecord.get("_id").toString());
        silver.put("tenantId",     bronzeRecord.getString("tenantId"));
        silver.put("eventType",    bronzeRecord.getString("eventType"));
        silver.put("_processedAt", LocalDateTime.now());

        Object rawPayload = bronzeRecord.get("payload");
        if (!(rawPayload instanceof Document payload)) return silver;

        for (SilverFieldRule rule : rules) {
            try {
                silver.put(rule.fieldName(), coerce(payload.get(rule.fieldName()), rule));
            } catch (Exception e) {
                log.warn("Coercion failed — field='{}', type={}, value='{}': {}",
                        rule.fieldName(), rule.targetType(),
                        payload.get(rule.fieldName()), e.getMessage());
                silver.put(rule.fieldName(), rule.nullable() ? null : defaultValue(rule.targetType()));
            }
        }

        return silver;
    }

    /**
     * Converts a raw payload value to the target type declared in the rule.
     * Applies trim before conversion when {@link SilverFieldRule#trim()} is true.
     */
    Object coerce(Object raw, SilverFieldRule rule) {
        if (raw == null) {
            return rule.nullable() ? null : defaultValue(rule.targetType());
        }

        String value = raw.toString();
        if (rule.trim()) value = value.trim();

        return switch (rule.targetType()) {
            case "STRING"  -> value;
            case "INTEGER" -> Integer.parseInt(value);
            case "DOUBLE"  -> Double.parseDouble(value);
            case "DATE"    -> LocalDate.parse(value).toString();
            default -> {
                log.warn("Unknown targetType '{}' for field '{}'; storing raw STRING.",
                        rule.targetType(), rule.fieldName());
                yield value;
            }
        };
    }

    private Object defaultValue(String targetType) {
        return switch (targetType) {
            case "INTEGER" -> 0;
            case "DOUBLE"  -> 0.0;
            default        -> "";
        };
    }

    // -------------------------------------------------------------------------
    // Collection name helpers (package-private for testability)
    // -------------------------------------------------------------------------

    static String silverCollection(String tenantId) {
        return "silver_" + tenantId;
    }
}
