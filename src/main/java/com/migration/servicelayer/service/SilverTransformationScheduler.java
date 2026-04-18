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
    private static final Criteria UNPROCESSED = Criteria.where("_processed").ne(true);

    private final MongoTemplate mongoTemplate;
    private final SilverMappingRulesRepository silverMappingRulesRepository;

    // -------------------------------------------------------------------------
    // Scheduled entry-point
    // -------------------------------------------------------------------------

    /**
     * Iterates over all tenant rule sets and drives per-tenant batch processing.
     * Uses {@code fixedDelay} (not {@code fixedRate}) so overlapping runs are
     * impossible — the next run starts only after the current one fully completes.
     */
    @Scheduled(fixedDelayString = "${silver.scheduler.fixed-delay-ms:300000}")
    public void processAllTenants() {
        List<SilverMappingRules> allRules = silverMappingRulesRepository.findAll();
        log.info("Silver scheduler triggered — {} rule set(s) found.", allRules.size());

        for (SilverMappingRules rules : allRules) {
            try {
                processTenant(rules);
            } catch (Exception e) {
                log.error("Silver processing failed for tenant='{}': {}",
                        rules.getTenantId(), e.getMessage(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-tenant processing
    // -------------------------------------------------------------------------

    void processTenant(SilverMappingRules rules) {
        String tenantId = rules.getTenantId();
        String bronzeCollection = bronzeCollection(tenantId);
        String silverCollection = silverCollection(tenantId);

        long total = mongoTemplate.count(Query.query(UNPROCESSED), bronzeCollection);
        if (total == 0) {
            log.debug("No unprocessed bronze records for tenant='{}'.", tenantId);
            return;
        }

        int totalBatches = (int) Math.ceil((double) total / BATCH_SIZE);
        log.info("Processing tenant='{}': {} record(s), {} batch(es).", tenantId, total, totalBatches);

        // try-with-resources on ExecutorService (AutoCloseable since Java 19):
        // close() calls shutdown() + awaitTermination(), ensuring all virtual
        // threads finish before we log completion or move to the next tenant.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int page = 0; page < totalBatches; page++) {
                final long skip = (long) page * BATCH_SIZE;
                executor.submit(() -> processBatch(rules, bronzeCollection, silverCollection, skip));
            }
        }

        log.info("Tenant '{}' silver processing complete.", tenantId);
    }

    // -------------------------------------------------------------------------
    // Batch processing
    // -------------------------------------------------------------------------

    void processBatch(SilverMappingRules rules,
                      String bronzeCollection,
                      String silverCollection,
                      long skip) {

        Query query = Query.query(UNPROCESSED).skip(skip).limit(BATCH_SIZE);
        List<Document> bronzeRecords = mongoTemplate.find(query, Document.class, bronzeCollection);
        if (bronzeRecords.isEmpty()) return;

        List<Document> silverRecords = bronzeRecords.stream()
                .map(record -> toSilver(record, rules.getFields()))
                .toList();

        mongoTemplate.insert(silverRecords, silverCollection);

        // Mark the originals as processed to prevent re-processing on subsequent runs.
        List<Object> ids = bronzeRecords.stream().map(d -> d.get("_id")).toList();
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").in(ids)),
                Update.update("_processed", true),
                bronzeCollection
        );

        log.debug("Batch done: {} record(s) → '{}' | '{}' marked processed.",
                silverRecords.size(), silverCollection, bronzeCollection);
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

    static String bronzeCollection(String tenantId) {
        return "bronze_" + tenantId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    static String silverCollection(String tenantId) {
        return "silver_" + tenantId.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
